import BaseCanvas.attributes
import CanvasServer.CanvasServiceImpl
import CanvasServer.RemoteClientContext
import io.grpc.Attributes
import io.grpc.ServerTransportFilter
import mu.KLogging

internal class CanvasServerTransportFilter(val canvasService: CanvasServiceImpl) : ServerTransportFilter() {

    override fun transportReady(attributes: Attributes): Attributes {
        fun getRemoteAddr(attributes: Attributes) = attributes.get(REMOTE_ADDR_KEY)?.toString() ?: "Unknown"

        val clientContext = RemoteClientContext(getRemoteAddr(attributes))
        canvasService.remoteClientContextMap.put(clientContext.remoteClientId, clientContext)

        return attributes {
            set(CLIENT_ID_KEY, clientContext.remoteClientId)
            setAll(attributes)
        }
    }

    override fun transportTerminated(attributes: Attributes?) {
        if (attributes == null) {
            logger.error { "Null attributes" }
        } else {
            attributes.get(CLIENT_ID_KEY)?.also { clientId ->
                val context = canvasService.remoteClientContextMap.remove(clientId)
                canvasService.onClientDisconnect(clientId)
                logger.info { "Disconnected ${if (context != null) "from $context" else "with invalid clientId: $clientId"}" }
            } ?: logger.error { "Missing clientId in transportTerminated()" }
        }
        super.transportTerminated(attributes)
    }

    companion object : KLogging() {
        internal val CLIENT_ID = "client-id"
        private const val REMOTE_ADDR = "remote-addr"
        internal val CLIENT_ID_KEY: Attributes.Key<String> = Attributes.Key.create(CLIENT_ID)
        private val REMOTE_ADDR_KEY: Attributes.Key<String> = Attributes.Key.create(REMOTE_ADDR)
    }
}
