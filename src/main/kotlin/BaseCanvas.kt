import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import math.Vector2D
import math.distance
import math.limit
import math.plusAssign
import math.sub
import math.toOffSet

object BaseCanvas {
    val size = 500 by 500

    data class Ball(val index: Int, val color: Color) {
        val pos = Vector2D()
    }

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
}