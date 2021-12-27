import MultiCanvas.Companion.UNASSIGNED_CLIENT_ID
import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.ForwardingClientCallListener.SimpleForwardingClientCallListener
import io.grpc.Metadata
import io.grpc.Metadata.ASCII_STRING_MARSHALLER
import io.grpc.MethodDescriptor
import mu.KLogging

class CanvasClientInterceptor(val clientIdName: String, val canvas: MultiCanvas) : ClientInterceptor {

    override fun <ReqT, RespT> interceptCall(
        method: MethodDescriptor<ReqT, RespT>,
        callOptions: CallOptions,
        next: Channel
    ): ClientCall<ReqT, RespT> =
        object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
            canvas.grpcService.channel.newCall(method, callOptions)
        ) {
            override fun start(responseListener: Listener<RespT>, metadata: Metadata) {
                super.start(
                    object : SimpleForwardingClientCallListener<RespT>(responseListener) {
                        val metaClientIdKey = Metadata.Key.of(clientIdName, ASCII_STRING_MARSHALLER)

                        override fun onHeaders(headers: Metadata?) {
                            if (headers == null) {
                                logger.error { "Missing headers" }
                            } else {
                                // Assign clientId from headers if not already assigned
                                headers.get(metaClientIdKey)?.also { clientId ->
                                    if (canvas.clientIdRef.compareAndSet(UNASSIGNED_CLIENT_ID, clientId))
                                        logger.info { "Assigned clientId: $clientId" }
                                } ?: logger.error { "Headers missing CLIENT_ID key" }
                            }
                            super.onHeaders(headers)
                        }
                    },
                    metadata
                )
            }
        }

    companion object : KLogging()
}
