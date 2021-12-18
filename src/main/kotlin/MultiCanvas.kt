import BaseCanvas.ClientContext
import BaseCanvas.drawBalls
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerMoveFilter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import math.Vector2D
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.atomic.AtomicReference

class MultiCanvas {
    // This is set in CanvasClientInterceptor
    val clientIdRef = AtomicReference(UNASSIGNED_CLIENT_ID)
    val clientContextMap = ConcurrentHashMap<String, ClientContext>()
    val writeChannel = Channel<Vector2D>(Channel.CONFLATED)
    val grpcService = CanvasService(this, "localhost")

    companion object {
        const val UNASSIGNED_CLIENT_ID = "unassigned"

        @JvmStatic
        fun main(argv: Array<String>) =
            k5(size = BaseCanvas.size) {
                val canvas = MultiCanvas()
                val count = CountDownLatch(1)

                newSingleThreadExecutor().execute {
                    // First, synchronously call connect in order to propagate a clientId back to the client
                    canvas.grpcService.also { client -> runBlocking { client.connect() } }
                    count.countDown()

                    canvas.grpcService
                        .also { client ->
                            runBlocking {
                                client.register(canvas.clientIdRef.get(), Color.Red, Color.Green)
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

                count.await()

                newSingleThreadExecutor().execute {
                    canvas.grpcService
                        .also { client ->
                            runBlocking {
                                client.writePositions(canvas.clientIdRef.get(), canvas.writeChannel)
                            }
                        }
                }

                newSingleThreadExecutor().execute {
                    canvas.grpcService
                        .also { client ->
                            runBlocking {
                                client.readPositions(canvas.clientContextMap)
                            }
                        }
                }

                show(
                    Modifier.pointerMoveFilter(
                        onMove = {
                            runBlocking {
                                canvas.writeChannel.send(Vector2D(it.x, it.y))
                            }
                            false
                        }
                    )
                ) { drawScope ->
                    canvas.clientContextMap.values
                        .forEach { clientContext ->
                            drawScope.drawBalls(clientContext.balls, clientContext.mousePos.get() ?: Vector2D(0f, 0f))
                        }
                }
            }
    }
}