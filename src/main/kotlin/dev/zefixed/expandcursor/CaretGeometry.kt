package dev.zefixed.expandcursor

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.CaretVisualAttributes
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import java.awt.Color
import java.awt.Point
import java.awt.RenderingHints
import java.awt.geom.Rectangle2D

internal enum class CaretKind { BAR, BLOCK, BOX, UNDERSCORE }

internal data class CaretRect(val x: Int, val y: Int, val width: Int, val height: Int)

internal data class CaretVerticalMetrics(val height: Int, val yOffset: Int)

internal object CaretGeometry {

    fun shapeOf(editor: Editor, attributes: CaretVisualAttributes): CaretKind = when (attributes.shape) {
        CaretVisualAttributes.Shape.BLOCK -> CaretKind.BLOCK
        CaretVisualAttributes.Shape.BOX -> CaretKind.BOX
        CaretVisualAttributes.Shape.UNDERSCORE -> CaretKind.UNDERSCORE
        CaretVisualAttributes.Shape.BAR -> CaretKind.BAR
        CaretVisualAttributes.Shape.DEFAULT ->
            if (editor.settings.isBlockCursor || !editor.isInsertMode) CaretKind.BLOCK else CaretKind.BAR
    }

    fun verticalMetricsOf(editor: Editor): CaretVerticalMetrics {
        val lineHeight = editor.lineHeight
        if (editor.settings.isFullLineHeightCursor) return CaretVerticalMetrics(lineHeight, 0)
        val metrics = fontMetrics(editor)
        val fontMetricsHeight = metrics.height
        val descent = metrics.descent
        val newDescent = descent + (lineHeight - fontMetricsHeight) / 2
        val topOverhang = fontMetricsHeight - lineHeight + newDescent - descent
        val bottomOverhang = descent - newDescent
        val caretHeight = (lineHeight + topOverhang + bottomOverhang).coerceIn(1, maxOf(lineHeight, fontMetricsHeight))
        return CaretVerticalMetrics(caretHeight, -topOverhang)
    }

    fun barWidthOf(editor: Editor, attributes: CaretVisualAttributes): Int {
        val defaultWidth = editor.settings.lineCursorWidth
        return JBUIScale.scale(attributes.getWidth(defaultWidth).toFloat()).toInt().coerceAtLeast(1)
    }

    fun charAdvanceOf(editor: Editor, x: Int, y: Int, line: Int, column: Int): Int {
        val next = editor.visualPositionToXY(com.intellij.openapi.editor.VisualPosition(line, column + 1))
        return if (next.y == y && next.x > x) next.x - x else spaceAdvanceOf(editor)
    }

    fun spaceAdvanceOf(editor: Editor): Int = fontMetrics(editor).charWidth(' ')

    fun colorOf(editor: Editor, attributes: CaretVisualAttributes): Color =
        attributes.color ?: editor.colorsScheme.getColor(EditorColors.CARET_COLOR) ?: defaultColor()

    fun rect(editor: Editor, caret: Caret, attributes: CaretVisualAttributes): CaretRect? {
        val visualLine = caret.visualPosition.line
        val column = caret.visualPosition.column
        val origin: Point = editor.visualPositionToXY(caret.visualPosition)
        val vertical = verticalMetricsOf(editor)
        if (vertical.height <= 0) return null
        val advance = charAdvanceOf(editor, origin.x, origin.y, visualLine, column)
        val y = origin.y + vertical.yOffset
        return when (shapeOf(editor, attributes)) {
            CaretKind.BLOCK -> CaretRect(origin.x, y, advance, vertical.height)
            CaretKind.BOX -> CaretRect(origin.x, y, advance, vertical.height)
            CaretKind.UNDERSCORE -> {
                val barHeight = (vertical.height * attributes.thickness.coerceIn(0.05f, 1.0f)).toInt().coerceAtLeast(1)
                CaretRect(origin.x, y + vertical.height - barHeight, advance, barHeight)
            }
            CaretKind.BAR -> CaretRect(origin.x, y, barWidthOf(editor, attributes), vertical.height)
        }
    }

    private fun fontMetrics(editor: Editor) =
        editor.contentComponent.getFontMetrics(editor.colorsScheme.getFont(EditorFontType.PLAIN))

    private fun defaultColor(): Color = JBColor(Color(0, 0, 0), Color(255, 255, 255))

    internal fun drawScaled(g: java.awt.Graphics2D, rect: CaretRect, scale: Double, color: Color) {
        val half = rect.height * scale / 2.0
        if (half < 0.5) return
        val y = rect.y + rect.height / 2.0 - half
        val old = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = color
        g.fill(Rectangle2D.Float(rect.x.toFloat(), y.toFloat(), rect.width.toFloat(), (half * 2).toFloat()))
        if (old != null) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, old)
        }
    }

    internal fun drawBox(g: java.awt.Graphics2D, rect: CaretRect, scale: Double, color: Color) {
        val half = rect.height * scale / 2.0
        if (half < 1.0) return
        val y = rect.y + rect.height / 2.0 - half
        val old = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = color
        g.draw(Rectangle2D.Float(rect.x.toFloat(), y.toFloat(), rect.width.toFloat(), (half * 2).toFloat()))
        if (old != null) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, old)
        }
    }
}
