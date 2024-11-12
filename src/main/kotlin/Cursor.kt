interface Cursor {
    var x: Int
    var y: Int
    fun moveTo(newX: Int, newY: Int)
    fun moveBy(deltaX: Int, deltaY: Int)
    fun updateCursorPosition()
    fun addPositionListener(listener: (Int, Int) -> Unit)
    fun saveState(): Cursor
    fun restoreState(prevCursor: Cursor)
}