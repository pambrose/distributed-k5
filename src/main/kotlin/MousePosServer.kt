import io.grpc.ServerBuilder

class MousePosServer(val port: Int) {
    val server = ServerBuilder.forPort(port).addService(CoordinateServiceImpl()).build()

    fun start() {
        server.start()
        println("Server started, listening on $port")
        Runtime.getRuntime()
            .addShutdownHook(
                Thread {
                    println("*** shutting down gRPC server since JVM is shutting down")
                    server.shutdown()
                    println("*** server shut down")
                }
            )
    }
}

fun main() {
    val port = System.getenv("PORT")?.toInt() ?: 50051
    MousePosServer(port).apply {
        start()
        server.awaitTermination()
    }
}
