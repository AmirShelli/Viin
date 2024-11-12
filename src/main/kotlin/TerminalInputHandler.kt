import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.terminal.Terminal

class TerminalInputHandler(override val editor: Editor, override val terminal: Terminal) : InputHandler {
    fun handleKey(event: KeyboardEvent) {
        when (event.key) {
            ":" -> handleCommandMode()
            else -> editor.moveCursor(event.key)
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
                editor.updateStatusMessage(commandBuffer.toString())
                val keyEvent = rawMode.readKey().key

                when {
                    keyEvent.length == 1 -> {
                        commandBuffer.append(keyEvent)
                    }

                    keyEvent == "Backspace" && commandBuffer.length > 1 -> {
                        if(commandBuffer.length > 1)
                            commandBuffer.deleteCharAt(commandBuffer.length - 1)
                    }

                    keyEvent == "Enter" -> {
                        return commandBuffer.toString()
                    }

                    keyEvent == "Escape" -> {
                        editor.updateStatusMessage("")
                        return ""
                    }
                }
            }
        }
    }

    private fun handleCommand(command: String) {
        when (command) {
            ":q" -> editor.quit()
            ":w" -> editor.saveToFile()
            ":wq", ":qw" -> {
                editor.saveToFile()
                editor.quit()
            }

            ":i" -> insertMode()
            ":f" -> editor.search()

            else -> {
                editor.updateErrorMessge( "Error: Unknown command '${command.substring(1)}'")
            }
        }
    }

    private fun insertMode() {
        editor.updateStatusMessage("Insertion Mode")
        terminal.enterRawMode().use { rawMode ->
            while (true) {
                editor.onRenderRequest()
                val key = rawMode.readKey().key
                when {
                    key.length == 1 -> editor.handleInsertion(key)
                    key.equals("Escape") -> return
                    key.equals("Backspace") -> editor.handleDeletion()
                    key.equals("Enter") -> editor.handleNewLine()
                    else -> editor.moveCursor(key)
                }
            }
        }
    }
}