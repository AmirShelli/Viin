package basics

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.terminal.Terminal
import java.io.File
import kotlin.math.min
import kotlin.system.exitProcess

var cursorX = 0
var cursorY = 0
var offsetY = 0
var offsetX = 0
val terminal = Terminal()

var content: MutableList<String> = mutableListOf()
var statusMessage = String()

enum class SearchDirection { FORWARD, BACKWARD }

var searchDirection = SearchDirection.FORWARD


fun main(args: Array<String>) {

    content = getContent(args[0])

    terminal.enterRawMode().use { rawMode ->
        while (true) {
            statusMessage = "Lines: ${content.size}, X: $cursorX, Y: $cursorY"
            refreshScreen()
            val event = rawMode.readKey()
            handleKey(event)
        }
    }
}

fun changeStatus(sb: StringBuilder) {
    statusMessage = sb.toString()
}

fun handleKey(event: KeyboardEvent) {
    when (event.key) {
        ":" -> {
            val sb = StringBuilder(":")
            terminal.enterRawMode().use { rawMode ->
                while (true) {
                    changeStatus(sb)
                    refreshScreen()
                    val command = rawMode.readKey().key
                    if (command.length == 1)
                        sb.append(command)
                    else if (command == "Enter") {
                        when (sb.toString()) {
                            ":q" -> quit()
                            ":f" -> search()
                            ":i" -> insert()
                            else -> {
                                statusMessage = "Error: Not a command ${sb.toString().substring(1)}"
                            }
                        }
                        break;
                    }
                }
            }

        }

        "ArrowDown" -> {
            if (cursorY < content.size - offsetY - 1) {
                if (cursorY == terminal.info.height - 2) {
                    offsetY += 1
                } else {
                    cursorY += 1
                }
                if (cursorY + offsetY == content.size - 1 && cursorX >= content.get(content.size - 1).length) {
                    cursorX = content.get(content.size - 1).length
                }
            }
        }

        "ArrowUp" -> {
            if (cursorY > 0) {

                cursorY -= 1
            } else if (offsetY > 0) {
                offsetY -= 1
            }
            if (cursorX > content.get(cursorY + offsetY).length - offsetX)
                cursorX = content.get(cursorY + offsetY).length - offsetX
        }

        "ArrowLeft" -> {
            if (cursorX > 0) cursorX -= 1
            else if (cursorX == 0) {
                if (offsetX > 0)
                    offsetX--
                else if (cursorY > 0) {
                    cursorX = terminal.info.width - 1
                    cursorY -= 1
                    offsetX = content.get(cursorY + offsetY).length - terminal.info.width
                }
            }
        }

        "ArrowRight" -> {
            if (cursorX < terminal.info.width)
                cursorX += 1
            if (cursorX == terminal.info.width)
                if (cursorX + offsetX < content.get(cursorY).length)
                    offsetX++
                else if (cursorY < content.size - 1 - offsetY) {
                    cursorX = 0
                    offsetX = 0
                    cursorY += 1
                }
        }
    }
}

fun insert() {
    statusMessage = "Insertion Mode"
    while (true) {
        refreshScreen()
        terminal.enterRawMode().use { rawMode ->
            val event = rawMode.readKey()
            if (event.key.length == 1)
                insertChar(cursorY + offsetY, cursorX + offsetX, event.key)
            else {
                when {
                    event.key.equals("Escape") -> return
                    event.key.equals("Backspace") -> deleteChar(cursorY + offsetY, cursorX + offsetX)
                    event.key.equals("Enter") -> addNewLine(cursorY + offsetY, cursorX + offsetX)
                    else -> handleKey(event)
                }
            }
        }
    }
}

fun deleteChar(posY: Int, posX: Int) {
    if (posX == 0 && posY != 0) {
        val newStr = content.get(posY - 1) + content.get(posY)
        content.set(posY - 1, newStr)
        content.removeAt(posY)
    } else if (posX != 0) {
        val sb = StringBuilder(content.get(posY))
        sb.deleteCharAt(posX - 1)
        content.set(posY, sb.toString())
        cursorX--
    }
}

fun addNewLine(posY: Int, posX: Int) {
    if (content.get(posY).length > posX) {
        val sub = content.get(posY).substring(posX)
        content.add(posY + 1, sub)
        val sb = StringBuilder(content.get(posY))
        sb.delete(posX, sb.length)
        content.set(posY, sb.toString())
    } else
        content.add(posY + 1, "")
    cursorY++
    cursorX = 0
}

fun insertChar(posY: Int, posX: Int, char: String) {
    val sb = StringBuilder(content.get(posY))
    sb.insert(posX, char)
    content.set(posY, sb.toString())
    if (cursorX == terminal.tabWidth)
        offsetX++
    else
        cursorX++
}

private fun quit() {
    terminal.cursor.move {
        clearScreen()
        setPosition(0, 0)
    }
    exitProcess(0)
}


fun search() {
    val prevCursorY = cursorY
    val prevCursorX = cursorX
    val prevStatusMessage = statusMessage

    val sb = StringBuilder("")
    var currentIdx = 0
    var currentLineIdx = 0
    cursorY = 0
    terminal.enterRawMode().use { rawMode ->
        while (true) {
            if (sb.toString() == "") {
                searchDirection = SearchDirection.FORWARD
                currentLineIdx = 0
                currentIdx = 0
                cursorX = 0
            }

            changeStatus(sb)
            refreshScreen()
            val query = rawMode.readKey().key

            if (query.length == 1)
                sb.append(query)
            else if (query == "Backspace" && sb.toString() != "")
                sb.deleteCharAt(sb.length - 1)

            if (query == "ArrowDown" || query == "ArrowRight")
                searchDirection = SearchDirection.FORWARD
            else if (query == "ArrowUp" || query == "ArrowLeft")
                searchDirection = SearchDirection.BACKWARD
            else {
                searchDirection = SearchDirection.FORWARD
                currentIdx = 0
                currentLineIdx = 0
            }

            for (i in 0..content.size - 1) {
                currentIdx += if (searchDirection == SearchDirection.FORWARD) 1 else -1

                if (currentIdx < 0)
                    currentIdx = 0

                val match = if (searchDirection == SearchDirection.FORWARD)
                    content[currentLineIdx].indexOf(sb.toString(), currentIdx)
                else content[currentLineIdx].lastIndexOf(sb.toString(), currentIdx)
                if (match != -1) {
                    cursorX = match
                    currentIdx = match
                    offsetY = currentLineIdx
                    break
                }
                cursorX = content.get(content.size - 1).length
                offsetY = content.size - 1

                if (searchDirection == SearchDirection.BACKWARD) {
                    currentLineIdx--
                    if (currentLineIdx == -1)
                        currentLineIdx = content.size - 1
                    currentIdx = content.get(currentLineIdx).length - 1
                } else {
                    currentLineIdx++
                    if (currentLineIdx == content.size)
                        currentLineIdx = 0
                    currentIdx = -1
                }
            }

            if (query == "Enter") {
                cursorX = prevCursorX
                cursorY = prevCursorY
                statusMessage = prevStatusMessage
                break
            }
        }
    }
}

fun getContent(filePath: String): MutableList<String> {
    val file = File(filePath)
    return file.readLines().toMutableList()
}

fun refreshScreen() {
    terminal.cursor.move {
        clearScreen()
    }
    val sb = StringBuilder().append("\r")
    for (i in 0..<terminal.info.height - 1) {
        if (i + offsetY < content.size) {
            if (offsetX < content.get(i + offsetY).length)
                sb.append(
                    content.get(i + offsetY)
                        .substring(
                            offsetX,
                            offsetX + min(terminal.info.width, content.get(i + offsetY).length - offsetX)
                        )
                )
            else
                sb.append("")
        } else
            sb.append("-")
        sb.append("\r\n")
    }

    sb.append(statusMessage)
    terminal.print(sb.toString())
    terminal.cursor.move {
        setPosition(cursorX, cursorY)
    }
}


