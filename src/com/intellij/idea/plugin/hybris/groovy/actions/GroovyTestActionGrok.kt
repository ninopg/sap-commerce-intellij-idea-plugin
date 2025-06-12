/*
 * This file is part of "SAP Commerce Developers Toolset" plugin for IntelliJ IDEA.
 * Copyright (C) 2019-2025 EPAM Systems <hybrisideaplugin@epam.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.intellij.idea.plugin.hybris.groovy.actions

import com.intellij.idea.plugin.hybris.common.HybrisConstants
import com.intellij.idea.plugin.hybris.common.utils.HybrisIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

class GroovyTestActionGrok : AnAction(
    "Start Background Task (Grok)",
    null,
    HybrisIcons.SPRING_BEAN
) {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        if (project != null) {
            val task = GroovyBackgroundTask(project)
            ProgressManager.getInstance().run(task)
        }
    }
}

class GroovyBackgroundTask(project: Project) : Task.Backgroundable(project, "Groovy background task (Grok)", true) {
    private var success: Boolean = false
    private var resultMessage: String = "Task completed"

    override fun run(indicator: ProgressIndicator) {
        indicator.text = "Starting background task..."
        indicator.isIndeterminate = false
        try {
            for (i in 0 until 10) {
                indicator.checkCanceled()
                indicator.fraction = (i+1).toDouble() / 10
                indicator.text = "Processing step ${i + 1} of 10..."
                Thread.sleep(1000)
            }
            success = true
            resultMessage = "Task completed successfully!"
        } catch (e: ProcessCanceledException) {
            success = false
            resultMessage = "Task was canceled"
            indicator.cancel()
            throw e
        } catch (e: Exception) {
            success = false
            resultMessage = "Task failed: ${e.message}"
            throw RuntimeException(e)
        }
    }

    override fun onSuccess() {
        showNotification(project, resultMessage, true)
    }

    override fun onThrowable(error: Throwable) {
        showNotification(project, "Task failed: ${error.message}", false)
    }

    override fun onCancel() {
        showNotification(project, "Task was canceled", false)
    }

    private fun showNotification(project: Project, message: String, isSuccess: Boolean) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(HybrisConstants.NOTIFICATION_GROUP_HYBRIS)
            .createNotification("Background task result", message,
                if (isSuccess) NotificationType.INFORMATION else NotificationType.ERROR)
            .notify(project)
    }

}