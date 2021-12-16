import BaseCanvas.drawBalls
import kotlinx.coroutines.runBlocking
import math.Vector2D
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.atomic.AtomicReference

fun main() =
    k5(size = BaseCanvas.size) {
        val mousePos = AtomicReference<Vector2D>()

        newSingleThreadExecutor().execute {
            MousePosClient("localhost")
                .use { client ->
                    client.apply {
                        runBlocking {
                            readPositions(mousePos)
                        }
                    }
                }
        }

        show { drawScope ->
            drawScope.drawBalls(BaseCanvas.balls, mousePos.get() ?: Vector2D(0f, 0f))
        }
    }
