import io.grpc.Attributes
import io.grpc.ServerTransportFilter

internal class CanvasServerTransportFilter(val canvasService: Connectable) : ServerTransportFilter() {

    override fun transportReady(attributes: Attributes): Attributes = canvasService.onClientConnect(attributes)

    override fun transportTerminated(attributes: Attributes) {
        canvasService.onClientDisconnect(attributes)
        super.transportTerminated(attributes)
    }
}