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
            val clientInfo =
                clientInfo {
                    this.active = true
                    this.clientId = clientId
                    this.even = even.value.toString()
                    this.odd = odd.value.toString()
                }
            stub.register(clientInfo)
        }

    suspend fun writePositions(clientId: String, channel: Channel<Vector2D>) =
        coroutineScope {
            flow {
                for (value in channel) {
                    val mousePos =
                        mousePosition {
                            this.clientId = clientId
                            this.x = value.x.toDouble()
                            this.y = value.y.toDouble()
                        }
                    emit(mousePos)
                }
            }.also { requestFlow ->
                stub.writeMousePos(requestFlow)
            }
        }

    suspend fun readPositions(mousePosMap: ConcurrentMap<String, ClientContext>) =
        coroutineScope {
            stub.readMousePos(Empty.getDefaultInstance())
                .collect { mousePosition ->
                    mousePosMap[mousePosition.clientId]?.also { clientContext ->
                        clientContext.mousePos.set(Vector2D(mousePosition.x.toFloat(), mousePosition.y.toFloat()))
                    }
                }
        }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}