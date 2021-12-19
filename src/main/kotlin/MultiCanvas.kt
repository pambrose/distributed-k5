import BaseCanvas.drawBalls
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerMoveFilter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.runBlocking
import math.Vector2D
import mu.KLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

class MultiCanvas {
    // clientId is set in CanvasClientInterceptor
    val clientIdRef = AtomicReference(UNASSIGNED_CLIENT_ID)
    val clientContextMap = ConcurrentHashMap<String, ClientContext>()
    val positionChannel = Channel<Vector2D>(CONFLATED)
    val grpcService = CanvasService(this, "localhost")
    val clientId get() = clientIdRef.get()

    companion object : KLogging() {
        const val UNASSIGNED_CLIENT_ID = "unassigned"

        @JvmStatic
        fun main(argv: Array<String>) =
            k5(size = BaseCanvas.size) {
                val canvas = MultiCanvas()

                // First, synchronously call connect in order to propagate a clientId back to the client
                runBlocking { canvas.grpcService.connect() }

                newSingleThreadExecutor().execute {
                    runBlocking {
                        canvas.grpcService.register(canvas.clientId, Color.Random, Color.Random)
                            .collect {
                                if (it.active) {
                                    println("Adding client ${it.clientId}")
                                    canvas.clientContextMap[it.clientId] =
                                        ClientContext(it.clientId, it.even.toColor(), it.odd.toColor())
                                } else {
                                    println("Removing client ${it.clientId}")
                                    canvas.clientContextMap.remove(it.clientId)
                                }
                            }
                    }
                }

                newSingleThreadExecutor().execute {
                    val periodicAction = PeriodicAction(5.seconds)
                    runBlocking {
                        periodicAction.attempt { println("Writing positions for ${canvas.clientId}") }
                        canvas.grpcService.writePositions(canvas.clientId, canvas.positionChannel)
                    }
                }

                newSingleThreadExecutor().execute {
                    runBlocking {
                        val pa1 = PeriodicAction(5.seconds)
                        val pa2 = PeriodicAction(5.seconds)
                        canvas.grpcService.readPositions(canvas.clientId)
                            .collect { position ->
                                pa1.attempt { println("Reading position for ${position.clientId}") }
                                canvas.clientContextMap[position.clientId]?.also { clientContext ->
                                    clientContext.positionRef.set(
                                        Vector2D(
                                            position.x.toFloat(),
                                            position.y.toFloat()
                                        )
                                    )
                                } ?: pa2.attempt { logger.error("Received unknown clientId: ${position.clientId}") }
                            }

                    }
                }

                val periodicAction = PeriodicAction(10.seconds)

                show(
                    Modifier.pointerMoveFilter(
                        onMove = {
                            runBlocking { canvas.positionChannel.send(Vector2D(it.x, it.y)) }
                            false
                        }
                    )
                ) { drawScope ->
                    periodicAction.attempt { println("Drawing for ${canvas.clientContextMap.size} -- ${canvas.clientContextMap.values}") }
                    canvas.clientContextMap.values.forEach { drawScope.drawBalls(it.balls, it.position) }
                }
            }
    }
}