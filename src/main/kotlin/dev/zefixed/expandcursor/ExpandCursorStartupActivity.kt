package dev.zefixed.expandcursor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class ExpandCursorStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().getService(ExpandCursorManager::class.java)
    }
}
