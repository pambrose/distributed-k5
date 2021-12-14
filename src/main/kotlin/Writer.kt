import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerMoveFilter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import math.Vector2D
import math.distance
import math.limit
import math.plusAssign
import math.sub
import math.toOffSet
import java.util.concurrent.Executors.newSingleThreadExecutor

fun main() = k5(size = 2000 by 2000) {
    val balls = List(250) { i -> Ball(i, if (i % 2 == 0) Color.Red else Color.Green) }
    val mousePos = Vector2D()
    val msgChannel = Channel<Pair<Float, Float>>(Channel.CONFLATED)

    newSingleThreadExecutor().execute {
        MousePosClient("localhost")
            .use { client ->
                client.apply {
                    runBlocking {
                        writePositions(msgChannel)
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
                    println("Mouse moved to $mousePos")
                    msgChannel.send(mousePos.x to mousePos.y)
                }
                false
            }
        )
    ) { drawScope -> drawScope.drawBalls(balls, mousePos) }
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