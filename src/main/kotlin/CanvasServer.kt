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
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
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
                    System.err.println("*** shutting down gRPC server since JVM is shutting down ***")
                    grpcServer.shutdown()
                    System.err.println("*** server shut down ***")
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
        val positionChannel = Channel<PositionMsg>(CONFLATED)
        private val clientInfoRef = AtomicReference<ClientInfoMsg>()

        val even get() = clientInfoRef.get().even
        val odd get() = clientInfoRef.get().odd

        fun assignClientInfo(clientInfo: ClientInfoMsg) {
            clientInfoRef.set(clientInfo)
        }

        suspend fun sendMessage(message: ClientInfoMsg) {
            clientInfoChannel.send(message)
        }

        override fun toString() = "ClientContext(clientId='$clientId')"
    }


    class CanvasServiceImpl : CanvasServiceGrpcKt.CanvasServiceCoroutineImplBase() {
        val clientContextMap = ConcurrentHashMap<String, ClientContext>()

        @Synchronized
        fun onClientDisconnect(clientContext: ClientContext) {
            runBlocking {
                logger.info { "Reporting ${clientContext.clientId} disconnection info to ${clientContextMap.size} clients: ${clientContextMap.keys}" }
                clientContextMap.values.forEach { clientContext ->
                    launch {
                        clientContext.sendMessage(
                            clientInfo {
                                this.active = false
                                this.clientId = clientContext.clientId
                                this.even = clientContext.even
                                this.odd = clientContext.odd
                            })
                    }
                }
            }
        }

        override suspend fun connect(request: Empty) = Empty.getDefaultInstance()

        override fun register(request: ClientInfoMsg): Flow<ClientInfoMsg> {
            // Lookup client context, which was added in CanvasServerTransportFilter
            val clientContext = clientContextMap[request.clientId] ?: error("Missing client id: ${request.clientId}")
            clientContext.assignClientInfo(request)
            logger.info { "Registering client: $clientContext" }

            // Notify all the clients what the colors for the new client are
            runBlocking {
                logger.info { "Reporting ${request.clientId} connection info to ${clientContextMap.size} clients: ${clientContextMap.keys}" }
                clientContextMap.values.forEach { launch { it.sendMessage(request) } }
            }
            // Deliver the client info back to the client
            return flow {
                for (clientInfo in clientContext.clientInfoChannel)
                    emit(clientInfo)
                logger.info { "Disconnected" }
            }
        }

        override suspend fun writePositions(requests: Flow<PositionMsg>): Empty =
            requests.collect { positionMsg ->
                clientContextMap.values.forEach { it.positionChannel.send(positionMsg) }
            }.let { Empty.getDefaultInstance() }

        override fun readPositions(request: ClientInfoMsg) =
            flow {
                val clientContext =
                    clientContextMap[request.clientId] ?: error("Missing client id: ${request.clientId}")
                for (elem in clientContext.positionChannel)
                    emit(elem)
            }

        companion object : KLogging()
    }
}