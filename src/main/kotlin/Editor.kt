import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.terminal.Terminal
import java.io.File
import kotlin.system.exitProcess

class Editor(
    private val cursor: Cursor,
    private val viewport: Viewport,
    private val renderer: Renderer,
    val filePath: String,
    private val terminal: Terminal

) {
    init {
        viewport.attachToCursor(cursor)
        }

    enum class SearchDirection { FORWARD, BACKWARD }

    private var terminalInputHandler = TerminalInputHandler(this, terminal)
    private var errorMessage = ""
    private var statusMessage = ""
    private val content: MutableList<String> = initContent(filePath)
    private var showError = false

    private fun initContent(filePath: String): MutableList<String> {
        val file = File(filePath)
        return file.readLines().toMutableList()
    }

    fun run() {
        terminal.enterRawMode().use { rawMode ->
            while (true) {
                if (showError) {
                    updateStatusMessage(errorMessage)
                } else {
                    updateStatusMessage("Lines: ${content.size}, X: ${cursor.x - viewport.offsetX}, Y: ${cursor.y - viewport.offsetY}")
                }

                terminalInputHandler.handleKey(rawMode.readKey())
            }
        }
    }

    fun updateStatusMessage(newStatusMessage: String) {
        statusMessage = newStatusMessage
        onRenderRequest()
    }

    fun updateErrorMessge(newErrorMessage: String) {
        errorMessage = newErrorMessage
        showError = true
    }

    fun onRenderRequest() {
        showError = false
        val visibleContent = getVisibleContent()
        renderer.refreshScreen(visibleContent, cursor, statusMessage)
    }

    private fun getVisibleContent(): List<String> {
        val endY = (viewport.offsetY + viewport.height).coerceAtMost(content.size)
        return content.subList(viewport.offsetY, endY).map { line ->
            if (viewport.offsetX < line.length) {
                line.substring(
                    viewport.offsetX,
                    (viewport.offsetX + viewport.width).coerceAtMost(line.length)
                )
            } else {
                ""
            }
        }
    }

    fun quit() {
        terminal.cursor.move {
            clearScreen()
            setPosition(0, 0)
        }
        exitProcess(0)
    }

    fun moveCursor(key: String) {
        when (key) {
            "ArrowUp" -> moveCursorUp()
            "ArrowDown" -> moveCursorDown()
            "ArrowLeft" -> moveCursorLeft()
            "ArrowRight" -> moveCursorRight()
            "End" -> moveCursorToEndOfLine()
            "Home" -> moveCursorToStartOfLine()
        }
    }

    private fun moveCursorDown() {
        if (cursor.y < content.size - 1) {
            val newX = cursor.x.coerceAtMost(content[cursor.y + 1].length)
            cursor.moveTo(newX, cursor.y + 1)
        }
    }

    private fun moveCursorUp() {
        if (cursor.y > 0) {
            val newX = cursor.x.coerceAtMost(content[cursor.y - 1].length)
            cursor.moveTo(newX, cursor.y - 1)
        }
    }

    private fun moveCursorToEndOfLine() {
        val lineLength = content[cursor.y].length
        cursor.moveTo(lineLength, cursor.y)
    }

    private fun moveCursorToStartOfLine() {
        cursor.moveTo(0, cursor.y)
    }

    private fun moveCursorRight() {
        val lineLength = content[cursor.y].length
        if (cursor.x < lineLength) {
            cursor.moveBy(1, 0)
        } else if (cursor.y + 1 < content.size) {
            cursor.moveTo(0, cursor.y + 1)
        }
    }

    private fun moveCursorLeft() {
        if (cursor.x > 0) {
            cursor.moveBy(-1, 0)
        } else if (cursor.y > 0) {
            val previousLineLength = content[cursor.y - 1].length
            cursor.moveTo(previousLineLength, cursor.y - 1)
        }
    }

    fun search() {
        val sb = StringBuilder("")
        val prevCursor = cursor.saveState()
        var direction = SearchDirection.FORWARD

        terminal.enterRawMode().use { rawModeScope ->
            while (true) {
                updateStatusMessage(sb.toString())

                val key = rawModeScope.readKey().key
                when {
                    key.length == 1 -> sb.append(key)
                    key == "Backspace" && sb.isNotEmpty() -> sb.deleteAt(sb.lastIndex)
                    key in listOf("ArrowDown", "ArrowRight") -> direction = SearchDirection.FORWARD
                    key in listOf("ArrowUp", "ArrowLeft") -> direction = SearchDirection.BACKWARD
                    key == "Enter" -> {
                        cursor.restoreState(prevCursor)
                        return
                    }
                }

                if (sb.isNotEmpty()) {
                    performSearch(sb.toString(), direction)
                }
            }
        }
    }

    private fun performSearch(query: String, direction: SearchDirection) {
        var lineIdx = cursor.y
        var charIdx = if (direction == SearchDirection.FORWARD) cursor.x + 1 else cursor.x - 1

        repeat(content.size) {
            val line = content[lineIdx]
            val match = if (direction == SearchDirection.FORWARD) {
                line.indexOf(query, charIdx)
            } else {
                line.lastIndexOf(query, charIdx)
            }

            if (match != -1) {
                val regex = Regex("\\w+\\b")
                val wordEnd = regex.find(line, match)?.range?.last
                    ?: line.length

                viewport.adjustOffsets(wordEnd, lineIdx)
                cursor.moveTo(match, lineIdx)
                return
            }

            lineIdx = (lineIdx + if (direction == SearchDirection.FORWARD) 1 else content.size - 1) % content.size
            charIdx = if (direction == SearchDirection.FORWARD) 0 else content[lineIdx].length - 1
        }
    }


    fun handleInsertion(char: String) {
        val linePosition = cursor.y + viewport.offsetY
        val charPosition = cursor.x + viewport.offsetX

        modifyContentAtCursor(linePosition) { currentLine, pos ->
            currentLine.insertCharAt(charPosition, char)
        }
        cursor.moveBy(1, 0)
    }

    fun handleDeletion() {
        val linePosition = cursor.y + viewport.offsetY
        val charPosition = cursor.x + viewport.offsetX

        if (charPosition > 0) {
            modifyContentAtCursor(linePosition) { currentLine, pos ->
                currentLine.removeCharAt(pos - 1)
            }
            cursor.moveBy(-1, 0)
        } else if (linePosition > 0) {
            cursor.moveTo(content[linePosition - 1].length, linePosition - 1)
            mergeWithPreviousLine(linePosition)
        }
    }

    private fun mergeWithPreviousLine(linePosition: Int) {
        val currentLine = content[linePosition]
        val previousLinePosition = linePosition - 1
        val previousLine = content[previousLinePosition]

        val mergedLine = previousLine + currentLine

        content[previousLinePosition] = mergedLine
        content.removeAt(linePosition)
    }

    private fun modifyContentAtCursor(linePosition: Int, modify: (String, Int) -> String) {
        val charPosition = cursor.x + viewport.offsetX

        val currentLine = content[linePosition]
        content[linePosition] = modify(currentLine, charPosition)
    }

    private fun String.insertCharAt(index: Int, char: String): String {
        return this.substring(0, index) + char + this.substring(index)
    }

    private fun String.removeCharAt(index: Int): String {
        return this.substring(0, index) + this.substring(index + 1)
    }


    fun handleNewLine() {
        val currentLine = content[cursor.y]

        if (cursor.x < currentLine.length) {
            val newLine = currentLine.substring(cursor.x)
            content.add(cursor.y + 1, newLine)

            content[cursor.y] = currentLine.substring(0, cursor.x)
        } else {
            content.add(cursor.y + 1, "")
        }
        cursor.moveTo(0, cursor.y + 1)
    }

    fun saveToFile() {
        File(filePath).printWriter().use { out ->
            content.forEach {
                out.println(it)
            }
        }
    }
}