import CanvasServer.Companion.CLIENT_ID
import androidx.compose.ui.graphics.Color
import com.github.pambrose.CanvasServiceGrpcKt
import com.google.protobuf.Empty
import io.grpc.ClientInterceptors
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flow
import math.Vector2D
import java.io.Closeable
import java.util.concurrent.TimeUnit

class CanvasService internal constructor(canvas: SharedCanvas, val channel: ManagedChannel) : Closeable {

  constructor(canvas: SharedCanvas, host: String = "localhost", port: Int = 50051) :
      this(canvas, ManagedChannelBuilder.forAddress(host, port).usePlaintext().build())

  private val interceptors = listOf(CanvasClientInterceptor(CLIENT_ID, canvas))
  private val intercept = ClientInterceptors.intercept(channel, interceptors)
  private val stub = CanvasServiceGrpcKt.CanvasServiceCoroutineStub(intercept)

  suspend fun connect() =
    coroutineScope {
      stub.connect(Empty.getDefaultInstance())
    }

  suspend fun register(clientId: String, ballCount: Int, even: Color, odd: Color) =
    coroutineScope {
      stub.register(clientInfo(clientId, ballCount, even, odd))
    }

  suspend fun listenForChanges() =
    coroutineScope {
      stub.listenForChanges(Empty.getDefaultInstance())
    }

  suspend fun writePositions(clientId: String, positionChannel: Channel<Vector2D>) =
    coroutineScope {
      stub.writePositions(
        flow {
          for (position in positionChannel)
            emit(mousePosition(clientId, position.x, position.y))
        })
    }

  suspend fun readPositions(clientId: String) =
    coroutineScope {
      stub.readPositions(clientInfo(clientId))
    }

  override fun close() {
    channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
  }
}