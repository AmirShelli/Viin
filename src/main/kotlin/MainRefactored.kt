import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.terminal.Terminal
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {

    val terminal = Terminal()  // Assuming you're using a terminal for input/output
    val viewport = Viewport(terminal.info.width, terminal.info.height - 1)
    val renderer = TerminalRenderer(terminal)
    val cursor = TerminalCursor(terminal, viewport)

    val content = initContent(args[0])  // Load or initialize your document content
    val editor = Editor(cursor, viewport, renderer, InputHandler(editor = null), content)  // Temp null for now

    // Now we can initialize the input handler with the editor itself
    val inputHandler = InputHandler(editor)
    editor.inputHandler = inputHandler  // Assign the inputHandler after the editor is created

    editor.run()
}

fun initContent(filePath: String): MutableList<String> {
    val file = File(filePath)
    return file.readLines().toMutableList()
}

class Editor(
    val cursor: Cursor,
    val viewport: Viewport,
    val renderer: Renderer,
    var inputHandler: InputHandler,
    val content: MutableList<String>
) {
    init {
        viewport.attachToCursor(cursor)
    }

    enum class SearchDirection { FORWARD, BACKWARD }

    var statusMessage = ""

    fun run() {
        terminal.enterRawMode().use { rawMode ->
            while (true) {
                // Render the screen
                updateStatusMessage("Lines: ${content.size}, X: ${cursor.x - viewport.offsetX}, Y: ${cursor.y - viewport.offsetY}")
                // Handle user input
                inputHandler.handleKey(rawMode.readKey())
            }
        }
    }

    fun updateStatusMessage(newStatusMessage: String) {
        statusMessage = newStatusMessage
        onRenderRequest()
    }

    fun onRenderRequest() {
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

    fun moveCursorToEndOfLine() {
        val lineLength = content[cursor.y].length
        cursor.moveTo(lineLength, cursor.y)
    }

    fun moveCursorToStartOfLine() {
        cursor.moveTo(0, cursor.y)
    }

    fun moveCursorRight() {
        val lineLength = content[cursor.y].length
        if (cursor.x < lineLength) {
            cursor.moveBy(1, 0)
        } else if (cursor.y + 1 < content.size) {
            // Move to the start of the next line
            cursor.moveTo(0, cursor.y + 1)
        }
        // No action if at the end of the content
    }

    fun moveCursorLeft() {
        if (cursor.x > 0) {
            cursor.moveBy(-1, 0)
        } else if (cursor.y > 0) {
            // Move to the end of the previous line
            val previousLineLength = content[cursor.y - 1].length
            cursor.moveTo(previousLineLength, cursor.y - 1)
        }
        // No action if at the beginning of the content
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
                    performSearch(sb.toString(), direction)  // cursor is updated directly
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


    // TODO INSERT MODE NOW!!!
    fun insertMode() {
        updateStatusMessage("Insertion Mode")
        terminal.enterRawMode().use { rawMode ->
            while (true) {
                onRenderRequest()
                val key = rawMode.readKey().key
                if (key.length == 1) {
//                    insertChar(key)
                }
                else {
                    when {
                        key.equals("Escape") -> return
                        key.equals("Backspace") -> deleteChar(cursorY + offsetY, cursorX + offsetX)
                        key.equals("Enter") -> addNewLine(cursorY + offsetY, cursorX + offsetX)
                        else -> moveCursor(key)
                    }
                }
            }
        }
    }
}

//TODO make an a adapter class to abstract the terminal

class InputHandler(val editor: Editor?) {
    fun handleKey(event: KeyboardEvent) {
        when (event.key) {
            ":" -> handleCommandMode()
            else -> editor?.moveCursor(event.key)
        }
    }

    private fun handleCommandMode() {
        val command = readCommandInput()
        if (command.isNotEmpty()) {
            handleCommand(command)
        }
    }

    private fun readCommandInput(): String {
        val commandBuffer = StringBuilder(":")
        terminal.enterRawMode().use { rawMode ->
            while (true) {
                editor?.updateStatusMessage(commandBuffer.toString())
                val keyEvent = rawMode.readKey().key

                when {
                    keyEvent.length == 1 -> {
                        commandBuffer.append(keyEvent)
                    }

                    keyEvent == "Backspace" && commandBuffer.length > 1 -> {
                        // Prevent removing the initial ':'
                        commandBuffer.deleteCharAt(commandBuffer.length - 1)
                    }

                    keyEvent == "Enter" -> {
                        return commandBuffer.toString()
                    }

                    keyEvent == "Escape" -> {
                        editor?.updateStatusMessage("")
                        return ""  // Return an empty command to indicate cancellation
                    }
                }
            }
        }
    }

    private fun handleCommand(command: String) {
        when (command) {
            ":q" -> editor?.quit()
//            ":w" -> editor?.save()
            ":wq", ":qw" -> {
//                editor?.save()
                editor?.quit()
            }

            ":i" -> editor?.insertMode()
            ":f" -> editor?.search()
            else -> editor?.updateStatusMessage("Error: Unknown command '${command.substring(1)}'")
        }
    }
}


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

interface Renderer {
    fun refreshScreen(
        visibleContent: List<String>,
        cursor: Cursor,
        statusMessage: String
    )
}

class TerminalRenderer(val terminal: Terminal) : Renderer {

    override fun refreshScreen(
        visibleContent: List<String>,
        cursor: Cursor,
        statusMessage: String
    ) {
        terminal.cursor.move {
            setPosition(0, 0)
            clearScreen()  // Clear the terminal screen
        }

        val sb = StringBuilder()

        // Render the visible content within the viewport
        for (i in 0..terminal.info.height - 2) {
            if (i < visibleContent.size)
                sb.append(visibleContent.get(i))
            else
                sb.append("-")
            sb.append("\r\n")
        }

        // Render the status message
        sb.append(statusMessage)

        // Print the final content to the terminal
        terminal.print(sb.toString())

        cursor.updateCursorPosition()
    }
}
