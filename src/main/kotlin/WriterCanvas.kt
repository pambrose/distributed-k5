import BaseCanvas.drawBalls
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerMoveFilter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import math.Vector2D
import java.util.concurrent.Executors.newSingleThreadExecutor

fun main() =
    k5(size = BaseCanvas.size) {
        val mousePos = Vector2D()
        val channel = Channel<Vector2D>(Channel.CONFLATED)

        newSingleThreadExecutor().execute {
            MousePosClient("localhost")
                .use { client ->
                    client.apply {
                        runBlocking {
                            writePositions(channel)
                        }
                    }
                }
        }

        show(
            Modifier.pointerMoveFilter(
                onMove = {
                    mousePos.x = it.x
                    mousePos.y = it.y
                    runBlocking {
                        channel.send(Vector2D(mousePos.x, mousePos.y))
                    }
                    false
                }
            )
        ) { drawScope ->
            drawScope.drawBalls(BaseCanvas.balls, mousePos)
        }
    }