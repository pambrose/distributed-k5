import CanvasServer.CanvasServiceImpl
import CanvasServer.ClientContext
import io.grpc.Attributes
import io.grpc.ServerTransportFilter
import mu.KLogging

internal class CanvasServerTransportFilter(val canvasService: CanvasServiceImpl) : ServerTransportFilter() {

    override fun transportReady(attributes: Attributes): Attributes {
        val remoteAddress = attributes.get(REMOTE_ADDR_KEY)?.toString() ?: "Unknown"
        val clientContext = ClientContext(remoteAddress)
        canvasService.clientContextMap[clientContext.clientId] = clientContext
        logger.info { "Connected to $clientContext" }
        return attributes {
            set(CLIENT_ID_KEY, clientContext.clientId)
            setAll(attributes)
        }
    }

    override fun transportTerminated(attributes: Attributes?) {
        if (attributes == null) {
            logger.error { "Null attributes" }
        } else {
            attributes.get(CLIENT_ID_KEY)?.also { clientId ->
                val clientContext = canvasService.clientContextMap.remove(clientId)
                if (clientContext == null)
                    logger.error { "Missing clientId $clientId in transportTerminated()" }
                else {
                    canvasService.onClientDisconnect(clientContext)
                    clientContext.markClose()
                }
                logger.info { "Disconnected ${if (clientContext != null) "from $clientContext" else "with invalid clientId: $clientId"}" }
            } ?: logger.error { "Missing clientIdKey in transportTerminated()" }
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
