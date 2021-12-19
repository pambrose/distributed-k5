import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.github.pambrose.ClientInfoMsg
import com.github.pambrose.PositionMsg
import io.grpc.Attributes
import math.Vector2D
import java.util.concurrent.atomic.AtomicReference
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

class ClientContext(val clientId: String, even: Color, odd: Color) {
    val closed = AtomicReference<Boolean>(false)
    val balls = List(5) { i -> BaseCanvas.Ball(i, if (i % 2 == 0) even else odd) }
    val positionRef = AtomicReference<Vector2D>()
    val position get() = positionRef.get() ?: Vector2D(0f, 0f)

    val isClosed get() = closed.get()
    val isOpen get() = !closed.get()

    override fun toString() = "ClientContext(clientId='$clientId')"
}

fun clientInfo(
    clientId: String,
    even: Color? = null,
    odd: Color? = null,
    block: ClientInfoMsg.Builder.() -> Unit = {}
): ClientInfoMsg =
    ClientInfoMsg.newBuilder().run {
        this.active = true
        this.clientId = clientId
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
