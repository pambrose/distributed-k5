import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.github.pambrose.MousePosition
import math.Vector2D
import math.distance
import math.limit
import math.plusAssign
import math.sub
import math.toOffSet

object BaseCanvas {
    val size = 2000 by 2000
    val balls = List(250) { i -> Ball(i, if (i % 2 == 0) Color.Red else Color.Green) }

    fun DrawScope.drawBalls(balls: List<Ball>, mousePos: Vector2D) {
        val minDistance = 15f
        val maxLimit = 10f
        balls.forEach { ball ->
            if (ball.index == balls.size - 1) {
                ball.pos += mousePos.copy().sub(ball.pos).limit(maxLimit)
            } else {
                val leader = balls[ball.index + 1]
                if (ball.pos.distance(leader.pos) > minDistance)
                    ball.pos += leader.pos.copy().sub(ball.pos).limit(maxLimit)
            }
            drawCircle(color = ball.color, radius = 30f, center = ball.pos.toOffSet())
        }
    }

    fun mousePosition(block: MousePosition.Builder.() -> Unit): MousePosition =
        MousePosition.newBuilder().let {
            block.invoke(it)
            it.build()
        }

    infix fun Int.by(other: Int) = Size(this.toFloat(), other.toFloat())
}