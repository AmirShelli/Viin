import com.github.ajalt.mordant.input.RawModeScope
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.terminal.Terminal

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

        sb.append(statusMessage)
        terminal.print(sb.toString())

        cursor.updateCursorPosition()
    }

    fun withRawMode(block: (rawMode: RawModeScope) -> Unit) {
        terminal.enterRawMode().use { rawMode ->
            block(rawMode)
        }
    }
}