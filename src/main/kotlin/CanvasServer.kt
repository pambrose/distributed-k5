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
import kotlinx.coroutines.delay
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
import kotlin.time.Duration.Companion.milliseconds

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
        var position = 0.toDouble() to 0.toDouble()

        val clientInfo get() = clientInfoRef.get()
        val ballCount get() = clientInfo.ballCount
        val even get() = clientInfo.even.toColor()
        val odd get() = clientInfo.odd.toColor()

        fun assignClientInfo(clientInfo: ClientInfoMsg) {
            clientInfoRef.set(clientInfo)
        }

        suspend fun sendClientInfoMessage(message: ClientInfoMsg) {
            clientInfoChannel.send(message)
        }

        suspend fun sendPositionMessage(message: PositionMsg) {
            positionChannel.send(message)
        }

        override fun close() {
            clientInfoChannel.close()
            positionChannel.close()
        }

        override fun toString() = "ClientContext(clientId='$clientId')"
    }

    class CanvasServiceImpl : CanvasServiceGrpcKt.CanvasServiceCoroutineImplBase() {
        val clientContextMap = ConcurrentHashMap<String, ClientContext>()

        val mapKeys get() = clientContextMap.keys
        val mapValues get() = clientContextMap.values

        fun getClientContext(clientId: String) =
            clientContextMap[clientId]
                ?: "ClientId not found: $clientId".let { msg ->
                    logger.error { msg }
                    error(msg)
                }

        fun assignClientContext(clientId: String, clientContext: ClientContext) {
            clientContextMap[clientId] = clientContext
        }

        fun onClientDisconnect(clientContext: ClientContext) {
            newSingleThreadExecutor().execute {
                runBlocking {
                    // This delay allows the disconnect process that reported this to finish
                    delay(250.milliseconds)
                    mapValues.forEach { it ->
                        it.sendClientInfoMessage(
                            clientInfo(
                                clientContext.clientId,
                                clientContext.ballCount,
                                clientContext.even,
                                clientContext.odd
                            ) {
                                active = false
                            })
                    }
                }
            }
        }

        override suspend fun connect(request: Empty) = Empty.getDefaultInstance()

        override suspend fun register(request: ClientInfoMsg): Empty {
            // TODO This needs some synchronization
            // Lookup client context, which was added in CanvasServerTransportFilter
            getClientContext(request.clientId).also { clientContext ->
                // Notify all the existing clients about the new client
                mapValues.forEach { it.sendClientInfoMessage(request) }
                clientContext.assignClientInfo(request)
            }
            return Empty.getDefaultInstance()
        }

        override fun listenForChanges(request: Empty) =
            flow {
                // Catch up with the already existing clients
                mapValues.forEach { clientContext ->
                    emit(clientInfo(
                        clientContext.clientId,
                        clientContext.ballCount,
                        clientContext.even,
                        clientContext.odd
                    ) {
                        x = clientContext.position.first
                        y = clientContext.position.second
                    })
                }

                while (true)
                    select<Unit> {
                        mapValues.forEach { clientContext ->
                            clientContext.clientInfoChannel.onReceive { msg ->
                                emit(msg)
                            }
                        }
                    }
            }

        override suspend fun writePositions(requests: Flow<PositionMsg>) =
            requests.collect { msg ->
                getClientContext(msg.clientId).position = msg.x to msg.y
                mapValues.forEach {
                    it.sendPositionMessage(msg)
                }
            }.let { Empty.getDefaultInstance() }

        override fun readPositions(request: ClientInfoMsg) =
            flow {
                for (elem in getClientContext(request.clientId).positionChannel)
                    emit(elem)
            }

        companion object : KLogging()
    }
}