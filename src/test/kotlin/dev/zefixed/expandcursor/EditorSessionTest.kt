package dev.zefixed.expandcursor

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.CaretVisualAttributes
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.testFramework.LightPlatform4TestCase
import dev.zefixed.expandcursor.settings.ExpandCursorSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.abs

class EditorSessionTest : LightPlatform4TestCase() {

    private fun newEditor(): Pair<com.intellij.openapi.editor.Document, com.intellij.openapi.editor.Editor> {
        val document = EditorFactory.getInstance().createDocument("hello\nworld\n")
        val editor = EditorFactory.getInstance().createEditor(document, project)
        return document to editor
    }

    @Test
    fun `attach hides carets and detach restores them`() {
        val (_, editor) = newEditor()
        try {
            val session = EditorSession(editor, ExpandCursorSettings.instance)
            session.attach()
            try {
                for (caret in editor.caretModel.allCarets) {
                    assertTrue(
                        "native caret must be hidden after attach",
                        EditorSession.isHiddenMarker(caret.visualAttributes),
                    )
                }
                val primary = editor.caretModel.primaryCaret
                val rect = CaretGeometry.rect(editor, primary, CaretVisualAttributes.getDefault())
                assertNotNull(rect)
                assertTrue(rect!!.width > 0)
                assertTrue(rect.height > 0)
                assertTrue(CaretGeometry.charAdvanceOf(editor, 0, 0, 0, 0) > 0)
                session.tick()
            } finally {
                session.detach()
            }
            assertFalse(
                "native caret attributes must be restored after detach",
                EditorSession.isHiddenMarker(editor.caretModel.primaryCaret.visualAttributes),
            )
        } finally {
            EditorFactory.getInstance().releaseEditor(editor)
        }
    }

    @Test
    fun `external attribute changes are captured and rehidden`() {
        val (_, editor) = newEditor()
        try {
            val session = EditorSession(editor, ExpandCursorSettings.instance)
            session.attach()
            try {
                val block = CaretVisualAttributes(Color.RED, CaretVisualAttributes.Weight.NORMAL, CaretVisualAttributes.Shape.BLOCK, 1f)
                editor.caretModel.primaryCaret.visualAttributes = block
                session.tick()
                assertEquals(block, session.originalAttributesFor(editor.caretModel.primaryCaret))
                assertTrue(EditorSession.isHiddenMarker(editor.caretModel.primaryCaret.visualAttributes))
            } finally {
                session.detach()
            }
        } finally {
            EditorFactory.getInstance().releaseEditor(editor)
        }
    }

    @Test
    fun `geometry yields block advance matching bar for single char line`() {
        val (_, editor) = newEditor()
        try {
            val advance = CaretGeometry.charAdvanceOf(editor, 0, 0, 0, 0)
            val spaceAdvance = CaretGeometry.spaceAdvanceOf(editor)
            assertEquals(advance.toLong(), spaceAdvance.toLong())
        } finally {
            EditorFactory.getInstance().releaseEditor(editor)
        }
    }

    @Test
    fun `paint draws nothing when editor is not focused`() {
        val (_, editor) = newEditor()
        try {
            val session = EditorSession(editor, ExpandCursorSettings.instance)
            session.attach()
            try {
                val image = BufferedImage(200, 60, BufferedImage.TYPE_INT_ARGB)
                val g = image.createGraphics()
                try {
                    session.paint(g)
                } finally {
                    g.dispose()
                }
                for (x in 0 until image.width) {
                    for (y in 0 until image.height) {
                        assertEquals(
                            "overlay must not paint without focus",
                            0,
                            image.getRGB(x, y) ushr 24,
                        )
                    }
                }
            } finally {
                session.detach()
            }
        } finally {
            EditorFactory.getInstance().releaseEditor(editor)
        }
    }

    @Test
    fun `short caret is vertically centered like native caret`() {
        val (_, editor) = newEditor()
        try {
            val lineTop = editor.visualPositionToXY(VisualPosition(0, 0)).y
            val lineHeight = editor.lineHeight
            val metrics = CaretGeometry.verticalMetricsOf(editor)
            if (editor.settings.isFullLineHeightCursor) {
                assertEquals(lineHeight, metrics.height)
                assertEquals(0, metrics.yOffset)
            } else {
                val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
                val fontMetricsHeight = editor.contentComponent.getFontMetrics(font).height
                assertEquals(fontMetricsHeight.toLong(), metrics.height.toLong())
                val topGap = metrics.yOffset
                val bottomGap = lineHeight - metrics.yOffset - metrics.height
                assertTrue("top gap $topGap and bottom gap $bottomGap must match native centering", abs(topGap - bottomGap) <= 1)
            }
            val rect = CaretGeometry.rect(editor, editor.caretModel.primaryCaret, CaretVisualAttributes.getDefault())
            assertNotNull(rect)
            assertEquals(lineTop + metrics.yOffset, rect!!.y)
        } finally {
            EditorFactory.getInstance().releaseEditor(editor)
        }
    }
}
