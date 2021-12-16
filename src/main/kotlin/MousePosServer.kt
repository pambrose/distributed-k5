import com.github.pambrose.CoordinateServiceGrpcKt
import com.github.pambrose.MousePosition
import com.google.protobuf.Empty
import io.grpc.Server
import io.grpc.ServerBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KLogging

class MousePosServer(val port: Int) {
    val server: Server = ServerBuilder.forPort(port).addService(CoordinateServiceImpl()).build()

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
            MousePosServer(port)
                .apply {
                    start()
                    server.awaitTermination()
                }
        }
    }

    class CoordinateServiceImpl : CoordinateServiceGrpcKt.CoordinateServiceCoroutineImplBase() {
        val channel = Channel<MousePosition>(Channel.CONFLATED)

        override suspend fun writeMousePos(requests: Flow<MousePosition>): Empty =
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
    }
}