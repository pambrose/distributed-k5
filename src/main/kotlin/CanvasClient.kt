import BaseCanvas.ClientContext
import BaseCanvas.clientInfo
import BaseCanvas.mousePosition
import BaseCanvas.ping
import androidx.compose.ui.graphics.Color
import com.github.pambrose.CanvasServiceGrpcKt
import com.google.protobuf.Empty
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import math.Vector2D
import java.io.Closeable
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class CanvasClient internal constructor(private val channel: ManagedChannel) : Closeable {
    private val stub = CanvasServiceGrpcKt.CanvasServiceCoroutineStub(channel)

    constructor(host: String, port: Int = 50051) :
            this(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build())

    suspend fun ping(id: String) =
        coroutineScope {
            val pingMsg = ping { this.id = id }
            while (true) {
                stub.ping(pingMsg)
                delay(1.seconds)
            }
        }


    suspend fun register(id: String, even: Color, odd: Color) =
        coroutineScope {
            // TODO Copilot
            val clientInfo =
                clientInfo {
                    this.id = id
                    this.even = even.value.toString()
                    this.odd = odd.value.toString()
                }
            stub.register(clientInfo)
        }

    suspend fun writePositions(id: String, channel: Channel<Vector2D>) =
        coroutineScope {
            flow {
                for (value in channel) {
                    val mousePos =
                        mousePosition {
                            this.id = id
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
                    mousePosMap[mousePosition.id]?.also { clientContext ->
                        clientContext.mousePos.set(Vector2D(mousePosition.x.toFloat(), mousePosition.y.toFloat()))
                    }
                }
        }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}