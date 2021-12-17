import BaseCanvas.ClientContext
import BaseCanvas.drawBalls
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerMoveFilter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import math.Vector2D
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.atomic.AtomicReference

class MultiCanvas {
    val clientId = AtomicReference("")
    val contextMap = ConcurrentHashMap<String, ClientContext>()
    val writeChannel = Channel<Vector2D>(Channel.CONFLATED)
    val grpcService = CanvasService(this, "localhost")

    companion object {
        @JvmStatic
        fun main(argv: Array<String>) =
            k5(size = BaseCanvas.size) {
                val canvas = MultiCanvas()

                newSingleThreadExecutor().execute {
                    canvas.grpcService
                        .use { client ->
                            runBlocking {
                                client.register(canvas.clientId.get(), Color.Red, Color.Green)
                                    .collect {
                                        if (it.active)
                                            canvas.contextMap[it.id] =
                                                ClientContext(it.id, Color(it.even.toULong()), Color(it.odd.toULong()))
                                        else
                                            canvas.contextMap.remove(it.id)
                                    }
                            }
                        }
                }

                newSingleThreadExecutor().execute {
                    canvas.grpcService
                        .use { client ->
                            runBlocking {
                                client.writePositions(canvas.clientId.get(), canvas.writeChannel)
                            }
                        }
                }

                newSingleThreadExecutor().execute {
                    canvas.grpcService
                        .use { client ->
                            runBlocking {
                                client.readPositions(canvas.contextMap)
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
                    canvas.contextMap
                        .forEach { id, clientContext ->
                            drawScope.drawBalls(clientContext.balls, clientContext.mousePos.get() ?: Vector2D(0f, 0f))
                        }
                }
            }
    }
}