import BaseCanvas.Ball
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.github.pambrose.ClientInfoMsg
import com.github.pambrose.PositionMsg
import io.grpc.Attributes
import math.Vector2D
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.TimeSource

class PeriodicAction(val threshold: Duration) {
    private val clock = TimeSource.Monotonic
    private var lastActivityTime = clock.markNow()
    private var firstTime = true

    fun attempt(block: () -> Unit) {
        if (firstTime || lastActivityTime.elapsedNow() >= threshold) {
            lastActivityTime = clock.markNow()
            firstTime = false
            block()
        }
    }
}

class ClientContext constructor(val clientId: String, ballCount: Int, even: Color, odd: Color) {
    constructor(msg: ClientInfoMsg) : this(msg.clientId, msg.ballCount, msg.even.toColor(), msg.odd.toColor())

    val balls: List<Ball> = List(ballCount) { i -> Ball(i, if (i % 2 == 0) even else odd) }
    val positionRef: AtomicReference<Vector2D> = AtomicReference(Vector2D(0f, 0f))

    val position: Vector2D get() = positionRef.get()

    fun updatePosition(x: Double, y: Double) {
        positionRef.set(Vector2D(x.toFloat(), y.toFloat()))
    }

    override fun toString() = "ClientContext(clientId='$clientId')"
}

fun clientInfo(
    clientId: String,
    ballCount: Int = -1,
    even: Color? = null,
    odd: Color? = null,
    firstTime: Boolean = false,
    block: ClientInfoMsg.Builder.() -> Unit = {}
): ClientInfoMsg =
    ClientInfoMsg.newBuilder().run {
        this.active = true
        this.firstTime = firstTime
        this.clientId = clientId
        this.ballCount = ballCount
        this.even = even?.value?.toString() ?: "unassigned"
        this.odd = odd?.value?.toString() ?: "unassigned"
        block.invoke(this)
        build()
    }

fun mousePosition(clientId: String, x: Float, y: Float): PositionMsg =
    PositionMsg.newBuilder().run {
        this.clientId = clientId
        this.x = x.toDouble()
        this.y = y.toDouble()
        build()
    }

fun attributes(block: Attributes.Builder.() -> Unit): Attributes =
    Attributes.newBuilder()
        .run {
            block(this)
            build()
        }

val Color.Companion.Random get() = Color((0..255).random(), (0..255).random(), (0..255).random())

fun String.toColor() = Color(this.toULong())

infix fun Int.by(other: Int) = Size(this.toFloat(), other.toFloat())

fun Float.bound(min: Float, max: Float) = max(min, min(max, this))

interface Connectable {
    fun onClientConnect(attributes: Attributes): Attributes
    fun onClientDisconnect(attributes: Attributes)
}
