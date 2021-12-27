import io.grpc.Attributes
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall
import io.grpc.Metadata
import io.grpc.Metadata.ASCII_STRING_MARSHALLER
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import mu.KLogging

class CanvasServerInterceptor(val clientIdName: String, val clientIdKey: Attributes.Key<String>) : ServerInterceptor {
    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        requestHeaders: Metadata,
        handler: ServerCallHandler<ReqT, RespT>
    ): ServerCall.Listener<ReqT> =
        handler.startCall(
            object : SimpleForwardingServerCall<ReqT, RespT>(call) {
                val metaClientIdKey = Metadata.Key.of(clientIdName, ASCII_STRING_MARSHALLER)

                override fun sendHeaders(headers: Metadata) {
                    try {
                        // CLIENT_ID was assigned in CanvasServerTransportFilter
                        call.attributes.get(clientIdKey)?.also { clientId ->
                            headers.put(metaClientIdKey, clientId)
                        } ?: logger.warn { "No client id found in call attributes" }
                    } catch (e: Exception) {
                        logger.error(e) { "Error setting client id" }
                    }
                    super.sendHeaders(headers)
                }
            },
            requestHeaders
        )

    companion object : KLogging()
}
