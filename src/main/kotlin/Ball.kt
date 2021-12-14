import androidx.compose.ui.graphics.Color
import math.Vector2D

class Ball(val index: Int, val color: Color = Color((0..255).random(), (0..255).random(), (0..255).random())) {
    val pos = Vector2D()
}