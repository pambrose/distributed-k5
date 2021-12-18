import BaseCanvas.ClientContext
import BaseCanvas.Random
import BaseCanvas.drawBalls
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerMoveFilter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.runBlocking
import math.Vector2D
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.atomic.AtomicReference

class MultiCanvas {
    // clientId is set in CanvasClientInterceptor
    val clientIdRef = AtomicReference(UNASSIGNED_CLIENT_ID)
    val clientContextMap = ConcurrentHashMap<String, ClientContext>()
    val positionChannel = Channel<Vector2D>(CONFLATED)
    val grpcService = CanvasService(this, "localhost")
    val clientId get() = clientIdRef.get()

    companion object {
        const val UNASSIGNED_CLIENT_ID = "unassigned"

        @JvmStatic
        fun main(argv: Array<String>) =
            k5(size = BaseCanvas.size) {
                val canvas = MultiCanvas()

                // First, synchronously call connect in order to propagate a clientId back to the client
                canvas.grpcService.also { client -> runBlocking { client.connect() } }

                newSingleThreadExecutor().execute {
                    canvas.grpcService
                        .also { client ->
                            runBlocking {
                                client.register(canvas.clientId, Color.Random, Color.Random)
                                    .collect {
                                        if (it.active)
                                            canvas.clientContextMap[it.clientId] =
                                                ClientContext(
                                                    it.clientId,
                                                    Color(it.even.toULong()),
                                                    Color(it.odd.toULong())
                                                )
                                        else
                                            canvas.clientContextMap.remove(it.clientId)
                                    }
                            }
                        }
                }

                newSingleThreadExecutor().execute {
                    canvas.grpcService
                        .also { client ->
                            runBlocking {
                                client.writePositions(canvas.clientId, canvas.positionChannel)
                            }
                        }
                }

                newSingleThreadExecutor().execute {
                    canvas.grpcService
                        .also { client ->
                            runBlocking {
                                client.readPositions(canvas.clientId, canvas.clientContextMap)
                            }
                        }
                }

                show(
                    Modifier.pointerMoveFilter(
                        onMove = {
                            runBlocking {
                                canvas.positionChannel.send(Vector2D(it.x, it.y))
                            }
                            false
                        }
                    )
                ) { drawScope ->
                    canvas.clientContextMap.values
                        .forEach { clientContext ->
                            drawScope.drawBalls(clientContext.balls, clientContext.position.get() ?: Vector2D(0f, 0f))
                        }
                }
            }
    }
}