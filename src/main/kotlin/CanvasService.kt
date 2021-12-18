import BaseCanvas.ClientContext
import BaseCanvas.clientInfo
import BaseCanvas.mousePosition
import androidx.compose.ui.graphics.Color
import com.github.pambrose.CanvasServiceGrpcKt
import com.google.protobuf.Empty
import io.grpc.ClientInterceptors
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flow
import math.Vector2D
import java.io.Closeable
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit

class CanvasService internal constructor(canvas: MultiCanvas, val channel: ManagedChannel) : Closeable {
    val interceptors = listOf(CanvasClientInterceptor(canvas))

    private val stub =
        CanvasServiceGrpcKt.CanvasServiceCoroutineStub(ClientInterceptors.intercept(channel, interceptors))

    constructor(canvas: MultiCanvas, host: String, port: Int = 50051) :
            this(canvas, ManagedChannelBuilder.forAddress(host, port).usePlaintext().build())

    suspend fun connect() =
        coroutineScope {
            stub.connect(Empty.getDefaultInstance())
        }

    suspend fun register(clientId: String, even: Color, odd: Color) =
        coroutineScope {
            stub.register(
                clientInfo {
                    active = true
                    this.clientId = clientId
                    this.even = even.value.toString()
                    this.odd = odd.value.toString()
                })
        }

    suspend fun writePositions(clientId: String, positionChannel: Channel<Vector2D>) =
        coroutineScope {
            stub.writePositions(
                flow {
                    for (position in positionChannel) {
                        emit(
                            mousePosition {
                                this.clientId = clientId
                                x = position.x.toDouble()
                                y = position.y.toDouble()
                            })
                    }
                })
        }

    suspend fun readPositions(clientId: String, mousePosMap: ConcurrentMap<String, ClientContext>) =
        coroutineScope {
            stub.readPositions(
                clientInfo {
                    active = true
                    this.clientId = clientId
                    this.even = ""
                    this.odd = ""
                })
                .collect { position ->
                    println("Reading position $position")
                    mousePosMap[position.clientId]?.also { clientContext ->
                        clientContext.position.set(Vector2D(position.x.toFloat(), position.y.toFloat()))
                    }
                }
        }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}