import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.github.pambrose.ClientInfoMsg
import com.github.pambrose.PositionMsg
import io.grpc.Attributes
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
        val balls = List(5) { i -> Ball(i, if (i % 2 == 0) even else odd) }
        val position = AtomicReference<Vector2D>()
    }

    fun clientInfo(block: ClientInfoMsg.Builder.() -> Unit): ClientInfoMsg =
        ClientInfoMsg.newBuilder().let {
            block.invoke(it)
            it.build()
        }

    fun mousePosition(block: PositionMsg.Builder.() -> Unit): PositionMsg =
        PositionMsg.newBuilder().let {
            block.invoke(it)
            it.build()
        }

    fun attributes(block: Attributes.Builder.() -> Unit): Attributes =
        Attributes.newBuilder()
            .run {
                block(this)
                build()
            }

    val Color.Companion.Random get() = Color((0..255).random(), (0..255).random(), (0..255).random())

    infix fun Int.by(other: Int) = Size(this.toFloat(), other.toFloat())
}