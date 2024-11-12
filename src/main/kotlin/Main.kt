import com.github.ajalt.mordant.terminal.Terminal
import kotlin.system.exitProcess

fun main(args: Array<String>) {

    if (args.isEmpty()) {
        println("You forgot to pass a parameter!")
        exitProcess(0)
    }

    val terminal = Terminal()
    val viewport = Viewport(terminal.info.width, terminal.info.height - 1)
    val renderer = TerminalRenderer(terminal)
    val cursor = TerminalCursor(terminal, viewport)

    val editor = Editor(cursor, viewport, renderer, filePath = args[0], terminal)

    editor.run()
}