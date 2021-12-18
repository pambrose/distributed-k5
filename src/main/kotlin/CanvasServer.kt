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
import java.util.concurrent.atomic.AtomicReference

class CanvasServer(val port: Int) {
    val canvasService = CanvasServiceImpl()
    val interceptors = mutableListOf<ServerInterceptor>(CanvasServerInterceptor())
    val grpcServer: Server =
        ServerBuilder
            .forPort(port)
            .addService(canvasService)
            .addService(ServerInterceptors.intercept(canvasService.bindService(), interceptors))
            .addTransportFilter(CanvasServerTransportFilter(canvasService))
            .build()

    fun start() {
        grpcServer.start()
        logger.info { "Server started, listening on $port" }
        Runtime.getRuntime()
            .addShutdownHook(
                Thread {
                    println("*** shutting down gRPC server since JVM is shutting down")
                    grpcServer.shutdown()
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
                    grpcServer.awaitTermination()
                }
        }
    }

    data class ClientContext(private val remoteAddr: String) {
        val clientId = UUID.randomUUID().toString()
        val clientInfoChannel = Channel<ClientInfoMsg>(UNLIMITED)
        val clientInfoRef = AtomicReference<ClientInfoMsg>()

        suspend fun sendMessage(message: ClientInfoMsg) {
            clientInfoChannel.send(message)
        }

        override fun toString() = "RemoteClientContext(remoteAddr='$remoteAddr', remoteClientId='$clientId')"
    }


    class CanvasServiceImpl : CanvasServiceGrpcKt.CanvasServiceCoroutineImplBase() {
        val clientContextMap = ConcurrentHashMap<String, ClientContext>()
        val positionChannel = Channel<PositionMsg>(Channel.CONFLATED)

        @Synchronized
        fun onClientDisconnect(clientContext: ClientContext) {
            runBlocking {
                // logger.info { "Client disconnected: ${clientContext.clientId}" }
                clientContextMap.values.forEach { clientContext ->
                    launch {
                        clientContext.sendMessage(
                            clientInfo {
                                this.active = false
                                this.clientId = clientContext.clientId
                                this.even = clientContext.clientInfoRef.get().even
                                this.odd = clientContext.clientInfoRef.get().odd
                            })
                    }
                }
            }
        }

        override suspend fun connect(request: Empty): Empty {
            return Empty.getDefaultInstance()
        }

        override fun register(request: ClientInfoMsg): Flow<ClientInfoMsg> {
            val entry = clientContextMap[request.clientId] ?: error("Missing client id: ${request.clientId}")
            entry.clientInfoRef.set(request)

            // Notify all the clients what the colors for the new client are
            runBlocking {
                clientContextMap.values.forEach { clientContext ->
                    launch {
                        clientContext.sendMessage(request)
                    }
                }
            }
            // Deliver the client info back to the client
            return flow {
                for (clientInfo in entry.clientInfoChannel)
                    emit(clientInfo)
                println("Disconnected")
            }
        }

        override suspend fun writeMousePos(requests: Flow<PositionMsg>): Empty =
            requests.collect {
                positionChannel.send(it)
            }.let { Empty.getDefaultInstance() }

        override fun readMousePos(request: Empty) =
            flow {
                for (elem in positionChannel)
                    emit(elem)
            }

        companion object : KLogging()
    }
}