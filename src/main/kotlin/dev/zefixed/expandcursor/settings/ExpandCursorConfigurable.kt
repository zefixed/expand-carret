package dev.zefixed.expandcursor.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class ExpandCursorConfigurable : Configurable {

    private var enabledCheckbox: JBCheckBox? = null
    private var periodSpinner: JBIntSpinner? = null

    override fun createComponent(): JComponent {
        val settings = ExpandCursorSettings.instance
        val checkbox = JBCheckBox("Enable expand caret animation", settings.state.enabled)
        val spinner = JBIntSpinner(settings.state.periodOverrideMillis, 0, ExpandCursorSettings.MAX_PERIOD, 50)
        enabledCheckbox = checkbox
        periodSpinner = spinner
        val form = FormBuilder.createFormBuilder()
            .addComponent(checkbox)
            .addLabeledComponent("Blink period override, ms (0 = follow IDE setting):", spinner)
            .panel
        return JPanel(BorderLayout()).apply { add(form, BorderLayout.NORTH) }
    }

    override fun isModified(): Boolean {
        val checkbox = enabledCheckbox ?: return false
        val settings = ExpandCursorSettings.instance
        return checkbox.isSelected != settings.state.enabled ||
            committedSpinnerNumber() != settings.state.periodOverrideMillis
    }

    override fun apply() {
        val checkbox = enabledCheckbox ?: return
        val settings = ExpandCursorSettings.instance
        settings.state.enabled = checkbox.isSelected
        settings.state.periodOverrideMillis = committedSpinnerNumber()
        settings.fireChanged()
    }

    override fun reset() {
        val settings = ExpandCursorSettings.instance
        enabledCheckbox?.isSelected = settings.state.enabled
        periodSpinner?.number = settings.state.periodOverrideMillis
    }

    override fun getDisplayName(): String = "Expand Cursor"

    private fun committedSpinnerNumber(): Int {
        val spinner = periodSpinner ?: return 0
        try {
            spinner.commitEdit()
        } catch (_: Exception) {
        }
        return spinner.number
    }
}
