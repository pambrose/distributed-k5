import com.github.pambrose.CoordinateServiceGrpcKt
import com.google.protobuf.Empty
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import java.io.Closeable
import java.util.concurrent.TimeUnit

class MousePosClient internal constructor(private val channel: ManagedChannel) : Closeable {
    private val stub = CoordinateServiceGrpcKt.CoordinateServiceCoroutineStub(channel)

    constructor(host: String, port: Int = 50051) :
            this(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build())

    suspend fun writePositions(channel: Channel<Pair<Float, Float>>) =
        coroutineScope {
            val requests =
                flow {
                    for (value in channel) {
                        val request = Msgs.mousePosition {
                            this.x = value.first.toDouble()
                            this.y = value.second.toDouble()
                        }
                        println("Emiting $request")
                        emit(request)
                    }
                }
        }

    suspend fun readPositions(channel: Channel<Pair<Float, Float>>) =
        coroutineScope {
            val replies = stub.readMousePos(Empty.getDefaultInstance())
            replies.collect { reply ->
                channel.send(Pair(reply.x.toFloat(), reply.y.toFloat()))
            }
        }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}