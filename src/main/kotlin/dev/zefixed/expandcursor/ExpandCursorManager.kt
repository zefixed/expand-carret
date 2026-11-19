package dev.zefixed.expandcursor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.ui.EditorTextField
import dev.zefixed.expandcursor.settings.ExpandCursorSettings
import dev.zefixed.expandcursor.settings.ExpandCursorSettingsListener
import javax.swing.Timer

@Service(Service.Level.APP)
class ExpandCursorManager : Disposable {

    private val settings: ExpandCursorSettings
        get() = ExpandCursorSettings.instance

    private val sessions = LinkedHashMap<Editor, EditorSession>()
    private val timer = Timer(FRAME_INTERVAL_MS) { reconcile() }

    init {
        EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorCreated(event: EditorFactoryEvent) {
                onEdt { reconcile() }
            }

            override fun editorReleased(event: EditorFactoryEvent) {
                onEdt { detach(event.editor) }
            }
        }, this)
        ApplicationManager.getApplication()?.messageBus?.connect(this)
            ?.subscribe(ExpandCursorSettingsListener.TOPIC, object : ExpandCursorSettingsListener {
                override fun settingsChanged() {
                    onEdt { reconcile() }
                }
            })
        onEdt { reconcile() }
        LOG.info("Expand cursor manager initialized")
    }

    fun reconcile() {
        val desired = HashSet<Editor>()
        for (editor in EditorFactory.getInstance().allEditors) {
            if (shouldAttach(editor)) {
                desired.add(editor)
                if (editor !in sessions) {
                    attach(editor)
                }
            }
        }
        for (editor in sessions.keys.toSet()) {
            if (editor !in desired) {
                detach(editor)
            }
        }
        for (session in sessions.values) {
            try {
                session.tick()
            } catch (t: Throwable) {
                LOG.error("Expand caret overlay tick failed", t)
            }
        }
        when {
            sessions.isEmpty() && timer.isRunning -> timer.stop()
            sessions.isNotEmpty() && !timer.isRunning -> timer.start()
        }
    }

    private fun shouldAttach(editor: Editor): Boolean {
        if (!settings.state.enabled) return false
        if (editor.isOneLineMode) return false
        if (editor.getUserData(EditorTextField.SUPPLEMENTARY_KEY) == true) return false
        if ((editor as? EditorEx)?.isRendererMode == true) return false
        return editor.settings.isBlinkCaret
    }

    internal fun isAttached(editor: Editor): Boolean = editor in sessions

    private fun attach(editor: Editor) {
        val session = EditorSession(editor, settings)
        sessions[editor] = session
        try {
            session.attach()
        } catch (t: Throwable) {
            sessions.remove(editor)
            LOG.error("Failed to attach expand caret overlay", t)
        }
    }

    private fun detach(editor: Editor) {
        sessions.remove(editor)?.let { session ->
            try {
                session.detach()
            } catch (t: Throwable) {
                LOG.error("Failed to detach expand caret overlay", t)
            }
        }
    }

    private fun onEdt(block: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application == null || application.isDispatchThread) {
            block()
        } else {
            application.invokeLater(block)
        }
    }

    override fun dispose() {
        timer.stop()
        for (editor in sessions.keys.toSet()) {
            detach(editor)
        }
        sessions.clear()
    }

    companion object {
        private const val FRAME_INTERVAL_MS = 16

        private val LOG = Logger.getInstance(ExpandCursorManager::class.java)
    }
}
