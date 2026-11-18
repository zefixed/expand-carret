package dev.zefixed.expandcursor

import java.awt.Graphics
import javax.swing.JComponent

internal class ExpandCaretOverlay(private val session: EditorSession) : JComponent() {

    init {
        isOpaque = false
        isFocusable = false
    }

    override fun contains(x: Int, y: Int): Boolean = false

    override fun paintComponent(g: Graphics) {
        session.paint(g as java.awt.Graphics2D)
    }
}
