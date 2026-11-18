package dev.zefixed.expandcursor

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import dev.zefixed.expandcursor.settings.ExpandCursorSettings

class ToggleExpandCursorAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val settings = ExpandCursorSettings.instance
        settings.state.enabled = !settings.state.enabled
        settings.fireChanged()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.text =
            if (ExpandCursorSettings.instance.state.enabled) "Disable Expand Cursor" else "Enable Expand Cursor"
    }
}
