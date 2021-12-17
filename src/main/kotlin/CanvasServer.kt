import BaseCanvas.clientInfo
import com.github.pambrose.CanvasServiceGrpcKt
import com.github.pambrose.ClientInfoMsg
import com.github.pambrose.PositionMsg
import com.google.protobuf.Empty
import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.ServerInterceptor
import io.grpc.ServerInterceptors
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KLogging
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class CanvasServer(val port: Int) {
    val canvasService = CanvasServiceImpl()
    val interceptors = mutableListOf<ServerInterceptor>(CanvasServerInterceptor())
    val grpc_server: Server =
        ServerBuilder
            .forPort(port)
            .addService(canvasService)
            .addService(ServerInterceptors.intercept(canvasService.bindService(), interceptors))
            .addTransportFilter(CanvasServerTransportFilter(canvasService))
            .build()

    fun start() {
        grpc_server.start()
        logger.info { "Server started, listening on $port" }
        Runtime.getRuntime()
            .addShutdownHook(
                Thread {
                    println("*** shutting down gRPC server since JVM is shutting down")
                    grpc_server.shutdown()
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
                    grpc_server.awaitTermination()
                }
        }
    }

    data class RemoteClientContext(private val remoteAddr: String) {
        val remoteClientId = UUID.randomUUID().toString()
    }

    data class ClientContext(val clientInfo: ClientInfoMsg, val channel: Channel<ClientInfoMsg>) {
        var timeStamp = System.currentTimeMillis()
    }

    class CanvasServiceImpl : CanvasServiceGrpcKt.CanvasServiceCoroutineImplBase() {
        val clientContextMap = ConcurrentHashMap<String, ClientContext>()
        val remoteClientContextMap = ConcurrentHashMap<String, RemoteClientContext>()
        val channel = Channel<PositionMsg>(Channel.CONFLATED)

//        init {
//            newSingleThreadExecutor().execute {
//                while (true) {
//                    Thread.sleep(1000000)
//                    clientContextMap.values.forEach {
//                        val age = System.currentTimeMillis() - it.timeStamp
//                        if (age >= 2000) {
//                            runBlocking {
//                                clientContextMap.forEach { id, clientContext ->
//                                    println("Client disconnected: ${it.clientInfo.id}")
//                                    clientContextMap.remove(it.clientInfo.id)
//                                    val removal = clientInfo {
//                                        active = false
//                                        id = it.clientInfo.id
//                                        even = it.clientInfo.even
//                                        odd = it.clientInfo.odd
//                                    }
//                                    launch {
//                                        clientContext.channel.send(removal)
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }

        // TODO CoPilot
        @Synchronized
        fun onClientDisconnect(clientId: String) {
            runBlocking {
                println("Client disconnected: ${clientId}")
                if (clientContextMap.containsKey(clientId)) {
                    val msg = clientInfo {
                        val entry = clientContextMap.remove(clientId) ?: error("Missing client id: $clientId")
                        active = false
                        id = entry.clientInfo.id
                        even = entry.clientInfo.even
                        odd = entry.clientInfo.odd
                    }
                    clientContextMap.values.forEach { clientContext ->
                        launch {
                            clientContext.channel.send(msg)
                        }
                    }
                }
            }
        }

        override fun register(request: ClientInfoMsg): Flow<ClientInfoMsg> {
            val channel = Channel<ClientInfoMsg>(UNLIMITED)
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

        override suspend fun writeMousePos(requests: Flow<PositionMsg>): Empty =
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