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

import com.intellij.icons.AllIcons
import com.intellij.idea.plugin.hybris.common.utils.HybrisIcons
import com.intellij.idea.plugin.hybris.toolwindow.ReloadEnvironmentsAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ActionUtil
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import javax.swing.Icon

class GroovyChooseWebContextActionGroup : DefaultActionGroup({ "Choose Spring Web Context" }, true)  {

    var currentAction : WebContextAction? = WebContextAction("/hac", HybrisIcons.Y.LOGO_GREEN, this)

    val separator = Separator.create()

    val reloadWebContextAction = ReloadWebContextAction()

    init {
        templatePresentation.icon = HybrisIcons.Y.LOGO_GREEN
        templatePresentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
    }

    override fun getChildren(e: AnActionEvent?): Array<out AnAction?> {
        val children = super.getChildren(e)
        val actions = children + mapOf(
            "default" to HybrisIcons.Y.LOGO_BLUE,
            "/hac/springmvc-v2" to HybrisIcons.Y.LOGO_GREEN,
            "/occ/springmvc-v2" to HybrisIcons.Y.LOGO_ORANGE,
            "/yacceleratorstorefront/springmvc" to HybrisIcons.Y.LOGO_RED,
        ).map { (name, icon) ->
            WebContextAction(name, icon, this)
        }.toTypedArray()
        return actions + separator + reloadWebContextAction
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val presentation = e.presentation

        // val hacSettings = RemoteConnectionUtil.getActiveRemoteConnectionSettings(project, RemoteConnectionType.Hybris)
        presentation.text = this.currentAction?.actionName ?: "Switch Web Context"
        this.currentAction?.icon?.let {  presentation.icon = it }
        // else hacSettings.shortenConnectionName()
        presentation.isEnabledAndVisible = true

        presentation.description = createHTML().div {
            p { +"Switch Web Context" }
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

}

class WebContextAction(val actionName: String, val icon: Icon, private val parent: GroovyChooseWebContextActionGroup) :
    AnAction(actionName, "", icon)
{

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        parent.currentAction = this
    }

}

class ReloadWebContextAction : AnAction("Reload Web Contexts", "Reloads the list of context", AllIcons.General.Refresh) {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {

    }

}