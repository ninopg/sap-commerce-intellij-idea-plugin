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

import com.intellij.idea.plugin.hybris.common.utils.HybrisIcons
import com.intellij.idea.plugin.hybris.tools.remote.RemoteConnectionType
import com.intellij.idea.plugin.hybris.tools.remote.RemoteConnectionUtil
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.stream.createHTML

class GroovyChooseWebContextActionGroup : DefaultActionGroup({ "Choose Spring Web Context" }, true)  {

    init {
        templatePresentation.icon = HybrisIcons.Y.REMOTE
        templatePresentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
    }

    override fun getChildren(e: AnActionEvent?): Array<out AnAction?> {
        val children = super.getChildren(e)
        return children + listOf("/hac","/occ/springmvc-v2","/yacceleratorstorefront/springmvc").map{name ->
            WebContextAction(name)
        }.toTypedArray()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val presentation = e.presentation

        // val hacSettings = RemoteConnectionUtil.getActiveRemoteConnectionSettings(project, RemoteConnectionType.Hybris)
        presentation.text = "web context" // hacSettings.shortenConnectionName() ?: "web context"
        // else hacSettings.shortenConnectionName()
        presentation.isEnabledAndVisible = true

        presentation.description = createHTML().div {
            p { +"Switch Web Context" }
        }
    }

}

class WebContextAction(private val actionName: String) :
    AnAction(actionName, "", HybrisIcons.Y.REMOTE_GREEN)
{

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        val presentation = e.presentation
        presentation.text = actionName
        presentation.icon = HybrisIcons.Y.REMOTE_GREEN
        presentation.isEnabledAndVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {

    }

}