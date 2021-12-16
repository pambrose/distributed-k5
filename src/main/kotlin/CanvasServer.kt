import BaseCanvas.clientInfo
import com.github.pambrose.CanvasServiceGrpcKt
import com.github.pambrose.ClientInfoPB
import com.github.pambrose.PingPB
import com.github.pambrose.PositionPB
import com.google.protobuf.Empty
import io.grpc.Server
import io.grpc.ServerBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors.newSingleThreadExecutor

class CanvasServer(val port: Int) {
    val server: Server = ServerBuilder.forPort(port).addService(CanvasServiceImpl()).build()

    fun start() {
        server.start()
        logger.info { "Server started, listening on $port" }
        Runtime.getRuntime()
            .addShutdownHook(
                Thread {
                    println("*** shutting down gRPC server since JVM is shutting down")
                    server.shutdown()
                    println("*** server shut down")
                }
            )
    }

    companion object : KLogging() {
        @JvmStatic
        fun main(argv: Array<String>) {
            val port = System.getenv("PORT")?.toInt() ?: 50051
            CanvasServer(port)
                .apply {
                    start()
                    server.awaitTermination()
                }
        }
    }

    data class ClientContext(val clientInfo: ClientInfoPB, val channel: Channel<ClientInfoPB>) {
        var timeStamp = System.currentTimeMillis()
    }

    class CanvasServiceImpl : CanvasServiceGrpcKt.CanvasServiceCoroutineImplBase() {
        val clientContextMap = ConcurrentHashMap<String, ClientContext>()
        val channel = Channel<PositionPB>(Channel.CONFLATED)

        init {
            newSingleThreadExecutor().execute {
                while (true) {
                    Thread.sleep(1000)
                    clientContextMap.values.forEach {
                        val age = System.currentTimeMillis() - it.timeStamp
                        if (age >= 2000) {
                            println("Client disconnected: ${it.clientInfo.id}")
                            clientContextMap.remove(it.clientInfo.id)
                            val removal = clientInfo {
                                active = false
                                id = it.clientInfo.id
                                even = it.clientInfo.even
                                odd = it.clientInfo.odd
                            }
                            runBlocking {
                                clientContextMap.forEach { id, clientContext ->
                                    launch {
                                        clientContext.channel.send(removal)
                                    }
                                }

                            }
                        }
                    }
                }
            }
        }

        override suspend fun ping(request: PingPB): Empty {
            val clientContext = clientContextMap[request.id]
            if (clientContext == null)
                logger.error { "Client ${request.id} not found" }
            else
                clientContext.timeStamp = System.currentTimeMillis()
            return Empty.getDefaultInstance()
        }

        override fun register(request: ClientInfoPB): Flow<ClientInfoPB> {
            val channel = Channel<ClientInfoPB>(UNLIMITED)
            clientContextMap[request.id] = ClientContext(request, channel)
            runBlocking {
                clientContextMap.forEach { id, clientContext ->
                    launch {
                        clientContext.channel.send(request)
                    }
                }
            }
            return flow {
                for (clientInfo in channel) {
                    emit(clientInfo)
                }
                println("Disconnected")
            }
        }

        override suspend fun writeMousePos(requests: Flow<PositionPB>): Empty =
            requests.collect {
                channel.send(it)
            }.let {
                Empty.getDefaultInstance()
            }

        override fun readMousePos(request: Empty) =
            flow {
                for (elem in channel)
                    emit(elem)
            }

        companion object : KLogging()
    }
}