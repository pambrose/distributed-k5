import BaseCanvas.drawBalls
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerMoveFilter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import math.Vector2D
import mu.KLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

class MultiCanvas {
    // clientId is set in CanvasClientInterceptor
    val clientIdRef = AtomicReference(UNASSIGNED_CLIENT_ID)
    val clientContextMap = ConcurrentHashMap<String, ClientContext>()
    val positionChannel = Channel<Vector2D>(CONFLATED)
    val grpcService = CanvasService(this, "localhost")
    val clientId get() = clientIdRef.get()

    val mapSize get() = clientContextMap.size
    val mapKeys get() = clientContextMap.keys
    val mapValues get() = clientContextMap.values

    companion object : KLogging() {
        const val UNASSIGNED_CLIENT_ID = "unassigned"

        @JvmStatic
        fun main(argv: Array<String>) =
            k5(size = BaseCanvas.size) {
                val canvas = MultiCanvas()

                // Call connect synchronously in order to propagate a clientId back to the client
                runBlocking { canvas.grpcService.connect() }
                runBlocking {
                    canvas.grpcService.register(
                        canvas.clientId,
                        Random.nextInt(90) + 10,
                        Color.Random,
                        Color.Random
                    )
                }

                newSingleThreadExecutor().execute {
                    runBlocking {
                        canvas.grpcService.listenForChanges()
                            .collect { msg ->
                                if (msg.active)
                                    ClientContext(
                                        msg.clientId,
                                        msg.ballCount,
                                        msg.even.toColor(),
                                        msg.odd.toColor()
                                    ).also { clientContext ->
                                        canvas.clientContextMap[msg.clientId] = clientContext
                                        // Give it a moment to establish it at the origin
                                        delay(250.milliseconds)
                                        clientContext.updatePosition(msg.x, msg.y)
                                    }
                                else
                                    canvas.clientContextMap.remove(msg.clientId)
                            }
                    }
                }

                newSingleThreadExecutor().execute {
                    runBlocking {
                        canvas.grpcService.writePositions(canvas.clientId, canvas.positionChannel)
                    }
                }

                newSingleThreadExecutor().execute {
                    runBlocking {
                        canvas.grpcService.readPositions(canvas.clientId)
                            .collect { position ->
                                canvas.clientContextMap[position.clientId]?.also { clientContext ->
                                    clientContext.updatePosition(position.x, position.y)
                                } ?: logger.error("Received unknown clientId: ${position.clientId}")
                            }

                    }
                }

                show(
                    Modifier.pointerMoveFilter(
                        onMove = {
                            runBlocking { canvas.positionChannel.send(Vector2D(it.x, it.y)) }
                            false
                        }
                    )
                ) { drawScope ->
                    canvas.mapValues.forEach { drawScope.drawBalls(it.balls, it.position) }
                }
            }
    }
}