import BaseCanvas.mousePosition
import com.github.pambrose.CoordinateServiceGrpcKt
import com.google.protobuf.Empty
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flow
import math.Vector2D
import java.io.Closeable
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class MousePosClient internal constructor(private val channel: ManagedChannel) : Closeable {
    private val stub = CoordinateServiceGrpcKt.CoordinateServiceCoroutineStub(channel)

    constructor(host: String, port: Int = 50051) :
            this(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build())

    suspend fun writePositions(channel: Channel<Vector2D>) =
        coroutineScope {
            flow {
                for (value in channel) {
                    val mousePos = mousePosition {
                        this.x = value.x.toDouble()
                        this.y = value.y.toDouble()
                    }
                    println("Emitting $mousePos")
                    emit(mousePos)
                }
            }.also { requestFlow ->
                stub.writeMousePos(requestFlow)
            }
        }

    suspend fun readPositions(mousePos: AtomicReference<Vector2D>) =
        coroutineScope {
            stub.readMousePos(Empty.getDefaultInstance())
                .collect { reply ->
                    mousePos.set(Vector2D(reply.x.toFloat(), reply.y.toFloat()))
                }
        }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}