import BaseCanvas.ClientContext
import BaseCanvas.drawBalls
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerMoveFilter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import math.Vector2D
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors.newSingleThreadExecutor

fun main() =
    k5(size = BaseCanvas.size) {
        val id = UUID.randomUUID().toString()
        val writeChannel = Channel<Vector2D>(Channel.CONFLATED)
        val contextMap = ConcurrentHashMap<String, ClientContext>()

        newSingleThreadExecutor().execute {
            CanvasClient("localhost").use { client -> runBlocking { client.ping(id) } }
        }

        newSingleThreadExecutor().execute {
            CanvasClient("localhost")
                .use { client ->
                    runBlocking {
                        client.register(id, Color.Red, Color.Green)
                            .collect {
                                contextMap[it.id] =
                                    ClientContext(it.id, Color(it.even.toULong()), Color(it.odd.toULong()))
                            }
                    }
                }
        }

        newSingleThreadExecutor().execute {
            CanvasClient("localhost")
                .use { client ->
                    runBlocking {
                        client.writePositions(id, writeChannel)
                    }
                }
        }

        newSingleThreadExecutor().execute {
            CanvasClient("localhost")
                .use { client ->
                    runBlocking {
                        client.readPositions(contextMap)
                    }
                }
        }

        show(
            Modifier.pointerMoveFilter(
                onMove = {
                    runBlocking {
                        writeChannel.send(Vector2D(it.x, it.y))
                    }
                    false
                }
            )
        ) { drawScope ->
            contextMap
                .forEach { id, clientContext ->
                    drawScope.drawBalls(clientContext.balls, clientContext.mousePos.get() ?: Vector2D(0f, 0f))
                }
        }
    }