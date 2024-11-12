import com.github.ajalt.mordant.terminal.Terminal

class TerminalCursor(private val terminal: Terminal, private val viewport: Viewport) : Cursor {
    override var x: Int = 0
    override var y: Int = 0

    private val positionListeners = mutableListOf<(Int, Int) -> Unit>()

    override fun addPositionListener(listener: (Int, Int) -> Unit) {
        positionListeners.add(listener)
    }

    private fun notifyPositionChanged() {
        for (listener in positionListeners) {
            listener(x, y)
        }
    }

    override fun moveTo(newX: Int, newY: Int) {
        x = newX.coerceAtLeast(0)
        y = newY.coerceAtLeast(0)

        notifyPositionChanged()
    }

    override fun moveBy(deltaX: Int, deltaY: Int) {
        moveTo(x + deltaX, y + deltaY)
    }

    override fun updateCursorPosition() {
        terminal.cursor.move {
            setPosition(x - viewport.offsetX, y - viewport.offsetY)
        }
    }

    override fun saveState(): Cursor {
        val newCursor = TerminalCursor(this.terminal, this.viewport)
        newCursor.y = this.y
        newCursor.x = this.x

        return newCursor
    }

    override fun restoreState(prevCursor: Cursor) {
        this.y = prevCursor.y
        this.x = prevCursor.x
        moveTo(x, y)
    }
}