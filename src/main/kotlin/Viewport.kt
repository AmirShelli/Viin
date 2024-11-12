class Viewport(
    val width: Int,
    val height: Int
) {
    var offsetX: Int = 0
    var offsetY: Int = 0
        private set

    fun attachToCursor(cursor: Cursor) {
        cursor.addPositionListener { x, y ->
            adjustOffsets(x, y)
        }
    }

    fun adjustOffsets(cursorX: Int, cursorY: Int) {
        // Adjust offsetY (vertical scrolling)
        if (cursorY < offsetY) {
            offsetY = cursorY
        } else if (cursorY >= offsetY + height) {
            offsetY = cursorY - height + 1
        }

        // Adjust offsetX (horizontal scrolling)
        if (cursorX < offsetX) {
            offsetX = cursorX
        } else if (cursorX >= offsetX + width) {
            offsetX = cursorX - width + 1
        }
    }
}