import BaseCanvas.drawBalls
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import math.Vector2D
import mu.KLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

class SharedCanvas(hostName: String = "localhost", port: Int = 50051) {
  // clientId is set in CanvasClientInterceptor
  val clientIdRef = AtomicReference(UNASSIGNED_CLIENT_ID)
  val clientContextMap = ConcurrentHashMap<String, ClientContext>()
  val positionChannel: Channel<Vector2D> = Channel(CONFLATED)
  val grpcService = CanvasService(this, hostName, port)
  val clientId: String get() = clientIdRef.get()

  companion object : KLogging() {
    const val UNASSIGNED_CLIENT_ID = "unassigned"

    @JvmStatic
    fun main(argv: Array<String>) =
      k5(size = BaseCanvas.size) {
        runBlocking {
          val canvas = SharedCanvas("localhost", 50051)

          // Call connect to propagate a clientId back to the client
          canvas.grpcService.connect()

          canvas.grpcService.register(canvas.clientId, Random.nextInt(90) + 10, Color.Random, Color.Random)

          launch {
            canvas.grpcService.listenForChanges()
              .collect { msg ->
                if (msg.active)
                  ClientContext(msg)
                    .also { clientContext ->
                      canvas.clientContextMap[msg.clientId] = clientContext
                      // Give it a moment to establish it at the origin if it is the first time
                      if (msg.firstTime)
                        delay(250.milliseconds)
                      clientContext.updatePosition(msg.x, msg.y)
                    }
                else
                  canvas.clientContextMap.remove(msg.clientId)
              }
          }

          launch {
            canvas.grpcService.writePositions(canvas.clientId, canvas.positionChannel)
          }

          launch {
            canvas.grpcService.readPositions(canvas.clientId)
              .collect { position ->
                canvas.clientContextMap[position.clientId]?.also { clientContext ->
                  clientContext.updatePosition(position.x, position.y)
                } ?: logger.error("Received unknown clientId: ${position.clientId}")
              }
          }

          show(
            Modifier.pointerInput(Unit) {
              detectDragGestures(
                onDrag = { change, point ->
                  runBlocking {
                    canvas.positionChannel.send(
                      Vector2D(
                        change.position.x.bound(0.0f, BaseCanvas.size.width - 15f),
                        change.position.y.bound(0.0f, BaseCanvas.size.height - 60f)
                      )
                    )
                  }
                }
              )
            }) { drawScope ->
            canvas.clientContextMap.values.forEach { drawScope.drawBalls(it.balls, it.position) }
          }
        }
      }
  }
}