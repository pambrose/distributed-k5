import com.github.pambrose.CoordinateServiceGrpcKt
import com.github.pambrose.MousePosition
import com.google.protobuf.Empty
import kotlinx.coroutines.flow.Flow

class CoordinateServiceImpl : CoordinateServiceGrpcKt.CoordinateServiceCoroutineImplBase() {

    override suspend fun writeMousePos(requests: Flow<MousePosition>): Empty {
        return super.writeMousePos(requests)
    }

    override fun readMousePos(request: Empty): Flow<MousePosition> {
        return super.readMousePos(request)
    }
}

