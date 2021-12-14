import com.github.pambrose.MousePosition

object Msgs {
    fun mousePosition(block: MousePosition.Builder.() -> Unit): MousePosition =
        MousePosition.newBuilder().let {
            block.invoke(it)
            it.build()
        }
}