import CanvasServerTransportFilter.Companion.CLIENT_ID
import CanvasServerTransportFilter.Companion.CLIENT_ID_KEY
import io.grpc.ForwardingServerCall
import io.grpc.Metadata
import io.grpc.Metadata.ASCII_STRING_MARSHALLER
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import mu.KLogging

class CanvasServerInterceptor : ServerInterceptor {
    override fun <ReqT, RespT> interceptCall(
        call: ServerCall<ReqT, RespT>,
        requestHeaders: Metadata,
        handler: ServerCallHandler<ReqT, RespT>
    ) =
        handler.startCall(
            object : ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
                override fun sendHeaders(headers: Metadata) {
                    // CLIENT_ID was assigned in CanvasServerTransportFilter
                    call.attributes.get(CLIENT_ID_KEY)?.also { clientId ->
                        headers.put(META_CLIENT_ID_KEY, clientId)
                    } ?: logger.warn { "No client id found in call attributes" }
                    super.sendHeaders(headers)
                }
            },
            requestHeaders
        )

    companion object : KLogging() {
        internal val META_CLIENT_ID_KEY = Metadata.Key.of(CLIENT_ID, ASCII_STRING_MARSHALLER)
    }
}
