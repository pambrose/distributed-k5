import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.github.pambrose.ClientInfoPB
import com.github.pambrose.PingPB
import com.github.pambrose.PositionPB
import math.Vector2D
import math.distance
import math.limit
import math.plusAssign
import math.sub
import math.toOffSet
import java.util.*
import java.util.concurrent.atomic.AtomicReference

object BaseCanvas {
    val size = 2000 by 2000

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

    class ClientContext(val id: String = UUID.randomUUID().toString(), even: Color, odd: Color) {
        val balls = List(250) { i -> Ball(i, if (i % 2 == 0) even else odd) }
        val mousePos = AtomicReference<Vector2D>()
    }

    fun ping(block: PingPB.Builder.() -> Unit): PingPB =
        PingPB.newBuilder().let {
            block.invoke(it)
            it.build()
        }

    fun clientInfo(block: ClientInfoPB.Builder.() -> Unit): ClientInfoPB =
        ClientInfoPB.newBuilder().let {
            block.invoke(it)
            it.build()
        }

    fun mousePosition(block: PositionPB.Builder.() -> Unit): PositionPB =
        PositionPB.newBuilder().let {
            block.invoke(it)
            it.build()
        }

    val Color.Companion.Random get() = Color((0..255).random(), (0..255).random(), (0..255).random())

    infix fun Int.by(other: Int) = Size(this.toFloat(), other.toFloat())
}