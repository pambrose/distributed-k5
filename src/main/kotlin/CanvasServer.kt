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
import kotlinx.coroutines.selects.select
import mu.KLogging
import java.io.Closeable
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

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

    class ClientContext(private val remoteAddr: String) : Closeable {
        val clientId = UUID.randomUUID().toString()
        val clientInfoChannel = Channel<ClientInfoMsg>(UNLIMITED)
        val positionChannel = Channel<PositionMsg>(CONFLATED)
        private val clientInfoRef = AtomicReference<ClientInfoMsg>()

        val clientInfo get() = clientInfoRef.get()
        val even get() = clientInfoRef.get().even.toColor()
        val odd get() = clientInfoRef.get().odd.toColor()

        fun assignClientInfo(clientInfo: ClientInfoMsg) {
            clientInfoRef.set(clientInfo)
        }

        suspend fun sendMessage(message: ClientInfoMsg) {
            clientInfoChannel.send(message)
        }

        override fun close() {
            clientInfoChannel.close()
            positionChannel.close()
        }

        override fun toString() = "ClientContext(clientId='$clientId')"
    }


    class CanvasServiceImpl : CanvasServiceGrpcKt.CanvasServiceCoroutineImplBase() {
        val clientContextMap = ConcurrentHashMap<String, ClientContext>()

        @Synchronized
        fun onClientDisconnect(clientContext: ClientContext) {
            logger.info { "Reporting ${clientContext.clientId} disconnection info to ${clientContextMap.size} clients: ${clientContextMap.keys}" }
            runBlocking {
                clientContextMap.values.forEach { clientContext ->
                    launch {
                        clientContext.sendMessage(
                            clientInfo(clientContext.clientId, clientContext.even, clientContext.odd) {
                                active = false
                            })
                    }
                }
            }
            clientContext.close()
        }

        override suspend fun connect(request: Empty) = Empty.getDefaultInstance()

        override fun register(request: ClientInfoMsg): Flow<ClientInfoMsg> {
            // Lookup client context, which was added in CanvasServerTransportFilter
            clientContextMap[request.clientId].also { clientContext ->
                if (clientContext == null) {
                    "Client context not found for clientId: ${request.clientId}".also { msg ->
                        logger.error { msg }
                        error(msg)
                    }
                } else {
                    clientContext.assignClientInfo(request)
                    logger.info { "Registering client: $clientContext" }

                    // Notify all the existing clients about the new client
                    runBlocking {
                        logger.info { "Reporting ${request.clientId} connection info to ${clientContextMap.size} clients: ${clientContextMap.keys}" }
                        clientContextMap.values.forEach { it.sendMessage(request) }
                    }

                    val preexisting =
                        clientContextMap.values
                            .filter { it.clientId != request.clientId }
                            .map { it.clientInfo }

                    // Deliver the client info back to the client
                    return flow {
                        for (item in preexisting)
                            emit(item)

                        while (true)
                            select<Unit> {
                                clientContextMap.values.forEach { cc ->
                                    cc.clientInfoChannel.onReceive { msg ->
                                        println("Sending client info $msg to client: $cc")
                                        emit(msg)
                                    }
                                }
                            }
                    }
                }
            }
        }

        override suspend fun writePositions(requests: Flow<PositionMsg>): Empty {
            val periodicAction = PeriodicAction(5.seconds)
            return requests.collect { positionMsg ->
                periodicAction.attempt { println("Reporting ${positionMsg.clientId} map size in writePositions() = ${clientContextMap.size} ${clientContextMap.values}") }
                clientContextMap.values.forEach { it.positionChannel.send(positionMsg) }
            }.let { Empty.getDefaultInstance() }
        }

        override fun readPositions(request: ClientInfoMsg) =
            flow {
                println("Reporting ${request.clientId} map size in readPositions() = ${clientContextMap.size} ${clientContextMap.values}")
                clientContextMap[request.clientId].also { clientContext ->
                    if (clientContext == null)
                        "Invalid clientId in readPosition(): ${request.clientId}".also { msg ->
                            logger.error { msg }
                            error(msg)
                        }
                    else
                        for (elem in clientContext.positionChannel)
                            emit(elem)
                }
            }

        companion object : KLogging()
    }
}