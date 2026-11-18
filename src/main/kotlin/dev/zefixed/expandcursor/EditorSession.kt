package dev.zefixed.expandcursor

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.CaretVisualAttributes
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.util.Disposer
import dev.zefixed.expandcursor.settings.ExpandCursorSettings
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

internal class EditorSession(
    private val editor: Editor,
    private val settings: ExpandCursorSettings,
) : Disposable {

    private val originalAttributes = WeakHashMap<Caret, CaretVisualAttributes>()
    private val phaseStartNanos = AtomicLong(System.nanoTime())
    private val overlay = ExpandCaretOverlay(this)
    private var previousRects: List<CaretRect> = emptyList()
    private var lastScale = -1f
    private var lastFocused = false

    private val componentListener = object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) {
            syncOverlayBounds()
        }
    }

    private val caretListener = object : CaretListener {
        override fun caretPositionChanged(event: CaretEvent) {
            restart()
        }

        override fun caretAdded(event: CaretEvent) {
        }

        override fun caretRemoved(event: CaretEvent) {
        }
    }

    private val documentListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            restart()
        }
    }

    private val focusListener = object : FocusChangeListener {
        override fun focusGained(editor: Editor) {
            restart()
        }

        override fun focusLost(editor: Editor) {
        }
    }

    fun attach() {
        val component = editor.contentComponent
        component.add(overlay)
        syncOverlayBounds()
        component.addComponentListener(componentListener)
        editor.caretModel.addCaretListener(caretListener)
        editor.document.addDocumentListener(documentListener, this)
        if (editor is EditorEx) {
            editor.addFocusListener(focusListener, this)
        }
        phaseStartNanos.set(System.nanoTime())
        lastScale = -1f
        lastFocused = false
        previousRects = emptyList()
        syncCaretAttributes()
        component.repaint()
    }

    fun detach() {
        Disposer.dispose(this)
        for (caret in editor.caretModel.allCarets) {
            if (isHiddenMarker(caret.visualAttributes)) {
                caret.visualAttributes = originalAttributes[caret] ?: CaretVisualAttributes.getDefault()
            }
        }
        originalAttributes.clear()
        overlay.parent?.remove(overlay)
        editor.contentComponent.removeComponentListener(componentListener)
        editor.contentComponent.repaint()
        previousRects = emptyList()
        lastScale = -1f
        lastFocused = false
    }

    fun tick() {
        val component = editor.contentComponent
        if (overlay.parent == null) return
        syncCaretAttributes()
        syncOverlayBounds()
        val showing = component.isShowing
        val focused = component.hasFocus()
        if (!showing || !focused) {
            phaseStartNanos.set(System.nanoTime())
            if (lastScale > 0f || lastFocused) {
                eraseOnce()
            }
            lastScale = 0f
            lastFocused = false
            previousRects = emptyList()
            return
        }
        val scale = ExpandCursorTiming.scaleAt(elapsedMillis(), ExpandCursorTiming.cycleMillis(halfPeriodMillis()))
        val rects = currentRects()
        val dirty = if (scale == 0f && lastScale == 0f) null else dirtyRegion(previousRects, rects)
        lastScale = scale
        lastFocused = true
        previousRects = rects
        dirty?.let { overlay.repaint(it.x, it.y, it.width, it.height) }
    }

    fun restart() {
        phaseStartNanos.set(System.nanoTime())
    }

    fun halfPeriodMillis(): Int = settings.effectiveHalfPeriodMillis(editor)

    internal fun originalAttributesFor(caret: Caret): CaretVisualAttributes? = originalAttributes[caret]

    fun elapsedMillis(): Long = (System.nanoTime() - phaseStartNanos.get()) / 1_000_000L

    fun paint(g: java.awt.Graphics2D) {
        val cycle = ExpandCursorTiming.cycleMillis(halfPeriodMillis())
        val scale = ExpandCursorTiming.scaleAt(elapsedMillis(), cycle).toDouble()
        if (scale <= 0.0) return
        for (caret in editor.caretModel.allCarets) {
            val attributes = originalAttributes[caret] ?: caret.visualAttributes.takeUnless(::isHiddenMarker) ?: continue
            val rect = CaretGeometry.rect(editor, caret, attributes) ?: continue
            val color = CaretGeometry.colorOf(editor, attributes)
            when (CaretGeometry.shapeOf(editor, attributes)) {
                CaretKind.BOX -> CaretGeometry.drawBox(g, rect, scale, color)
                else -> CaretGeometry.drawScaled(g, rect, scale, color)
            }
        }
    }

    override fun dispose() {
        editor.caretModel.removeCaretListener(caretListener)
    }

    private fun syncOverlayBounds() {
        val component = editor.contentComponent
        if (overlay.width != component.width || overlay.height != component.height) {
            overlay.setBounds(0, 0, component.width, component.height)
        }
    }

    private fun syncCaretAttributes() {
        for (caret in editor.caretModel.allCarets) {
            val current = caret.visualAttributes
            if (!isHiddenMarker(current)) {
                originalAttributes[caret] = current
                caret.visualAttributes = HIDDEN
            }
        }
    }

    private fun currentRects(): List<CaretRect> =
        editor.caretModel.allCarets.mapNotNull { caret ->
            originalAttributes[caret]?.let { CaretGeometry.rect(editor, caret, it) }
        }

    private fun eraseOnce() {
        dirtyRegion(previousRects, emptyList())?.let { overlay.repaint(it.x, it.y, it.width, it.height) }
    }

    private fun dirtyRegion(previous: List<CaretRect>, current: List<CaretRect>): Rectangle? {
        val all = previous + current
        if (all.isEmpty()) return null
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        for (r in all) {
            minX = minOf(minX, r.x)
            minY = minOf(minY, r.y)
            maxX = maxOf(maxX, r.x + r.width)
            maxY = maxOf(maxY, r.y + r.height)
        }
        return Rectangle(minX - 2, minY - 2, maxX - minX + 5, maxY - minY + 5)
    }

    companion object {
        val HIDDEN = CaretVisualAttributes(null, CaretVisualAttributes.Weight.NORMAL, CaretVisualAttributes.Shape.BAR, 0f)

        fun isHiddenMarker(attributes: CaretVisualAttributes): Boolean =
            attributes.shape == CaretVisualAttributes.Shape.BAR && attributes.thickness == 0f
    }
}
