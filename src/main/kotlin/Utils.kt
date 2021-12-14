import androidx.compose.ui.geometry.Size

infix fun Int.by(other: Int) = Size(this.toFloat(), other.toFloat())
