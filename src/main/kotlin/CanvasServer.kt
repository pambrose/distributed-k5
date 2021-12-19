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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import mu.KLogging
import java.io.Closeable
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors.newSingleThreadExecutor
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
        val closed = AtomicReference(false)
        val clientInfoChannel = Channel<ClientInfoMsg>(UNLIMITED)
        val positionChannel = Channel<PositionMsg>(CONFLATED)
        private val clientInfoRef = AtomicReference<ClientInfoMsg>()

        val clientInfo get() = clientInfoRef.get()
        val even get() = clientInfoRef.get().even.toColor()
        val odd get() = clientInfoRef.get().odd.toColor()
        val isClosed get() = closed.get()
        val isOpen get() = !closed.get()

        fun assignClientInfo(clientInfo: ClientInfoMsg) {
            clientInfoRef.set(clientInfo)
        }

        suspend fun sendClientInfoMessage(message: ClientInfoMsg) {
            if (isClosed)
                logger.warn { "Attempted to send clientInfo message to closed client $clientId" }
            else
                clientInfoChannel.send(message)
        }

        suspend fun sendPositionMessage(message: PositionMsg) {
            if (isClosed)
                logger.warn { "Attempted to send position message to closed client $clientId" }
            else
                positionChannel.send(message)
        }

        fun markClose() {
            closed.set(true)
        }

        override fun close() {
            clientInfoChannel.close()
            positionChannel.close()
        }

        override fun toString() = "ClientContext(clientId='$clientId')"
    }

    class CanvasServiceImpl : CanvasServiceGrpcKt.CanvasServiceCoroutineImplBase() {
        private val clientContextMap = ConcurrentHashMap<String, ClientContext>()

        val mapSize get() = clientContextMap.filter { it.value.isOpen }.size
        val mapKeys get() = clientContextMap.filter { it.value.isOpen }.keys
        val mapValues get() = clientContextMap.filter { it.value.isOpen }.values

        fun getClientContext(clientId: String): ClientContext? {
            val clientContext = clientContextMap[clientId]
            if (clientContext == null) {
                "ClientId not found: $clientId".also { msg ->
                    logger.error { msg }
                    //error(msg)
                }
            } else if (clientContext.isClosed) {
                "ClientId is closed: $clientId".also { msg ->
                    logger.error { msg }
                    //error(msg)
                }
            }
            return clientContext
        }

        fun assignClientContext(clientId: String, clientContext: ClientContext) {
            clientContextMap[clientId] = clientContext
        }

        @Synchronized
        fun onClientDisconnect(clientContext: ClientContext) {
            newSingleThreadExecutor().execute {
                logger.info { "Reporting ${clientContext.clientId} disconnection info to $mapSize clients: $mapKeys" }
                runBlocking {
                    mapValues.forEach { it ->
                        it.sendClientInfoMessage(
                            clientInfo(clientContext.clientId, clientContext.even, clientContext.odd) {
                                active = false
                            })
                    }
                }
            }
        }

        override suspend fun connect(request: Empty) = Empty.getDefaultInstance()

        override suspend fun register(request: ClientInfoMsg): Empty {
            // Lookup client context, which was added in CanvasServerTransportFilter
            getClientContext(request.clientId)?.also { clientContext ->
                // TODO sync this
                logger.info { "Registering client: $clientContext" }

                // Notify all the existing clients about the new client
                logger.info { "Reporting ${request.clientId} connection info to $mapSize clients: $mapKeys" }
                mapValues.forEach { it.sendClientInfoMessage(request) }

                clientContext.assignClientInfo(request)
            }
            return Empty.getDefaultInstance()
        }

        override fun listenForChanges(request: Empty): Flow<ClientInfoMsg> {
            return flow {
                // Catch up with the already existing clients
                mapValues.forEach { emit(it.clientInfo) }

                while (true)
                    select<Unit> {
                        mapValues.forEach { cc ->
                            cc.clientInfoChannel.onReceive { msg ->
                                //delay(1.seconds)
                                println("Sending client info for ${msg.clientId} to: $cc")
                                emit(msg)
                            }
                        }
                    }

            }
        }

        override suspend fun writePositions(requests: Flow<PositionMsg>): Empty {
            val periodicAction = PeriodicAction(5.seconds)
            return requests.collect { positionMsg ->
                periodicAction.attempt { println("Reporting ${positionMsg.clientId} map size in writePositions() = $mapSize $mapKeys") }
                mapValues.forEach { it.sendPositionMessage(positionMsg) }
            }.let { Empty.getDefaultInstance() }
        }

        override fun readPositions(request: ClientInfoMsg) =
            flow {
                println("Reporting ${request.clientId} map size in readPositions() = $mapSize $mapKeys")
                getClientContext(request.clientId)?.also { clientContext ->
                    for (elem in clientContext.positionChannel)
                        emit(elem)
                }
            }

        companion object : KLogging()
    }
}