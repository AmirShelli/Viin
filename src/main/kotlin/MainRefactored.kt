import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.terminal.Terminal
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val terminal = Terminal()  // Assuming you're using a terminal for input/output
    val cursor = TerminalCursor(terminal)
    val viewport = Viewport(terminal.info.width, terminal.info.height - 1)
    val renderer = TerminalRenderer(terminal)

    val content = mutableListOf<String>()  // Load or initialize your document content
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

    enum class SearchDirection { FORWARD, BACKWARD }

    var searchDirection = SearchDirection.FORWARD

    var statusMessage = ""

    fun run() {
        terminal.enterRawMode().use { rawMode ->
            while (true) {
                statusMessage = "Lines: ${content.size}, X: ${cursor.x}, Y: ${cursor.y}"

                // Calculate visible content based on viewport offsets
                val visibleContent = getVisibleContent()

                // Render the screen
                renderer.refreshScreen(visibleContent, cursor, statusMessage)

                // Handle user input
                inputHandler.handleKey(rawMode.readKey())
            }
        }
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
                "-"
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
        TODO("Not yet implemented")
    }

    private fun moveCursorUp() {
        TODO("Not yet implemented")
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
}

class InputHandler(val editor: Editor?) {
    fun handleKey(event: KeyboardEvent) {
        when (event.key) {
            ":" -> handleCommandMode()
            else -> editor?.moveCursor(event.key)  // Delegate cursor movement to Editor
        }
    }

    private fun handleCommandMode() {
        val sb = StringBuilder(":")
        terminal.enterRawMode().use { rawMode ->
            while (true) {
                //TODO() should rederer depend on the editor???
//                editor.changeStatus(sb.toString())
//                editor?.renderer?.refreshScreen()
                val command = rawMode.readKey().key
                if (command.length == 1)
                    sb.append(command)
                else if (command == "Enter") {
                    handleCommand(sb.toString())  // Process the command
                    break
                }
            }
        }
    }

    private fun handleCommand(command: String) {
        when (command) {
            ":q" -> editor?.quit()
//            ":f" -> editor.search()
//            ":i" -> editor.insert()
//            else -> editor.updateStatus("Error: Not a command ${command.substring(1)}")
        }
    }
}


interface Cursor {
    var x: Int
    var y: Int
    fun moveTo(newX: Int, newY: Int)
    fun moveBy(deltaX: Int, deltaY: Int)
}


class TerminalCursor(private val terminal: Terminal) : Cursor {
    override var x: Int = 0
    override var y: Int = 0

    private val positionListeners = mutableListOf<(Int, Int) -> Unit>()

    fun addPositionListener(listener: (Int, Int) -> Unit) {
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
        terminal.cursor.move {
            setPosition(x, y)
        }
    }

    override fun moveBy(deltaX: Int, deltaY: Int) {
        moveTo(x + deltaX, y + deltaY)
    }

}

class Viewport(
    val width: Int,
    val height: Int
) {
    var offsetX: Int = 0
        private set
    var offsetY: Int = 0
        private set

    fun attachToCursor(cursor: TerminalCursor) {
        cursor.addPositionListener { x, y ->
            adjustOffsets(x, y)
        }
    }

    private fun adjustOffsets(cursorX: Int, cursorY: Int) {
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

class TerminalRenderer(private val terminal: Terminal) : Renderer {

    override fun refreshScreen(
        visibleContent: List<String>,
        cursor: Cursor,
        statusMessage: String
    ) {
        terminal.cursor.move {
            clearScreen()  // Clear the terminal screen
        }

        val sb = StringBuilder()

        // Render the visible content within the viewport
        visibleContent.forEach { line ->
            sb.append(line).append("\r\n")
        }

        // Render the status message
        sb.append(statusMessage)

        // Print the final content to the terminal
        terminal.print(sb.toString())
    }
}





