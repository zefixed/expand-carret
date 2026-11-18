package dev.zefixed.expandcursor.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.editor.Editor
import com.intellij.util.messages.Topic

interface ExpandCursorSettingsListener {
    fun settingsChanged()

    companion object {
        val TOPIC: Topic<ExpandCursorSettingsListener> =
            Topic.create("Expand Cursor settings changed", ExpandCursorSettingsListener::class.java)
    }
}

@Service(Service.Level.APP)
@State(name = "ExpandCursorSettings", storages = [Storage("expand-cursor.xml")])
class ExpandCursorSettings : PersistentStateComponent<ExpandCursorSettings.State> {

    class State {
        var enabled: Boolean = true
        var periodOverrideMillis: Int = 0
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun effectiveHalfPeriodMillis(editor: Editor): Int =
        if (myState.periodOverrideMillis in MIN_PERIOD..MAX_PERIOD) myState.periodOverrideMillis
        else editor.settings.caretBlinkPeriod

    fun fireChanged() {
        ApplicationManager.getApplication()?.messageBus
            ?.syncPublisher(ExpandCursorSettingsListener.TOPIC)
            ?.settingsChanged()
    }

    companion object {
        const val MIN_PERIOD = 10
        const val MAX_PERIOD = 1500

        val instance: ExpandCursorSettings
            get() = ApplicationManager.getApplication().getService(ExpandCursorSettings::class.java)
    }
}

