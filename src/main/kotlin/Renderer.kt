interface Renderer {
    fun refreshScreen(
        visibleContent: List<String>,
        cursor: Cursor,
        statusMessage: String
    )
}