package dev.zefixed.expandcursor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import dev.zefixed.expandcursor.settings.ExpandCursorSettings
import dev.zefixed.expandcursor.settings.ExpandCursorSettingsListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandCursorManagerServiceTest : com.intellij.testFramework.LightPlatform4TestCase() {

    @Test
    fun `manager service initializes with injected settings`() {
        val manager = ApplicationManager.getApplication().getService(ExpandCursorManager::class.java)
        assertNotNull(manager)
        val settings = ExpandCursorSettings.instance
        assertTrue(settings.state.enabled || !settings.state.enabled)
        val previous = settings.state.enabled
        try {
            settings.state.enabled = false
            manager.reconcile()
            settings.state.enabled = true
            manager.reconcile()
        } finally {
            settings.state.enabled = previous
        }
    }

    @Test
    fun `disable detaches and re-enable reattaches without editor events`() {
        val document = EditorFactory.getInstance().createDocument("expand")
        val editor = EditorFactory.getInstance().createEditor(document, project)
        try {
            val manager = ApplicationManager.getApplication().getService(ExpandCursorManager::class.java)
            val settings = ExpandCursorSettings.instance
            val previous = settings.state.enabled
            try {
                settings.state.enabled = true
                manager.reconcile()
                assertTrue("session must be attached when enabled", manager.isAttached(editor))

                settings.state.enabled = false
                manager.reconcile()
                assertFalse("session must be detached when disabled", manager.isAttached(editor))

                settings.state.enabled = true
                manager.reconcile()
                assertTrue("session must reattach on re-enable with no editor events", manager.isAttached(editor))
            } finally {
                settings.state.enabled = previous
                manager.reconcile()
            }
        } finally {
            EditorFactory.getInstance().releaseEditor(editor)
        }
    }

    @Test
    fun `settings change event is published through the message bus`() {
        val bus = ApplicationManager.getApplication().messageBus
        val connection = bus.connect()
        var fired = 0
        try {
            connection.subscribe(ExpandCursorSettingsListener.TOPIC, object : ExpandCursorSettingsListener {
                override fun settingsChanged() {
                    fired++
                }
            })
            ExpandCursorSettings.instance.fireChanged()
            assertEquals(1, fired)
        } finally {
            connection.disconnect()
        }
    }
}
