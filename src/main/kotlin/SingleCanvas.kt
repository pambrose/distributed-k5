import BaseCanvas.ClientContext
import BaseCanvas.drawBalls
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerMoveFilter
import math.Vector2D

fun main() =
    k5(size = BaseCanvas.size) {
        val clientContext = ClientContext(even = Color.Red, odd = Color.Green)

        show(
            Modifier.pointerMoveFilter(
                onMove = {
                    clientContext.position.set(Vector2D(it.x, it.y))
                    false
                }
            )
        ) { drawScope ->
            drawScope.drawBalls(clientContext.balls, clientContext.position.get() ?: Vector2D(0f, 0f))
        }
    }