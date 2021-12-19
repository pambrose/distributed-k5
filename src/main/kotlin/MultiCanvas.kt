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
                runBlocking { canvas.grpcService.register(canvas.clientId, Color.Random, Color.Random) }

                newSingleThreadExecutor().execute {
                    runBlocking {
                        canvas.grpcService.listenForChanges()
                            .collect {
                                if (it.active)
                                    canvas.clientContextMap[it.clientId] =
                                        ClientContext(it.clientId, it.even.toColor(), it.odd.toColor())
                                else
                                    canvas.clientContextMap.remove(it.clientId)
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
                                    clientContext.positionRef.set(
                                        Vector2D(
                                            position.x.toFloat(),
                                            position.y.toFloat()
                                        )
                                    )
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