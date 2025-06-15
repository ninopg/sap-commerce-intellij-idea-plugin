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

package com.intellij.idea.plugin.hybris.actions

import com.intellij.icons.AllIcons
import com.intellij.idea.plugin.hybris.common.utils.HybrisIcons
import com.intellij.idea.plugin.hybris.groovy.GroovyHACService
import com.intellij.idea.plugin.hybris.settings.components.DeveloperSettingsComponent
import com.intellij.idea.plugin.hybris.tools.remote.RemoteConnectionType
import com.intellij.idea.plugin.hybris.tools.remote.RemoteConnectionUtil
import com.intellij.idea.plugin.hybris.tools.remote.http.HybrisHacHttpClient
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ActionUtil
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.stream.createHTML
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.swing.Icon

class GroovyChooseWebContextActionGroup : DefaultActionGroup({ "Choose Spring Web Context" }, true)  {

    var defaultAction : WebContextAction? = WebContextAction("default", HybrisIcons.Y.LOGO_BLUE, this)

    var currentAction : WebContextAction? = defaultAction

    val separator = Separator.create()

    val reloadWebContextAction = ReloadWebContextAction(this)

    val webContexts: MutableList<String> = mutableListOf("default")

    init {
        templatePresentation.icon = HybrisIcons.Y.LOGO_GREEN
        templatePresentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
    }

    override fun getChildren(e: AnActionEvent?): Array<out AnAction?> {
        val children = super.getChildren(e)

        val actions = children + webContexts.map { name ->
            WebContextAction(name, HybrisIcons.Y.LOGO_BLUE, this)
        }.toTypedArray()

        return actions + separator + reloadWebContextAction
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val presentation = e.presentation

        val groovySettings = DeveloperSettingsComponent.getInstance(project).state.groovySettings
        if (groovySettings.disableScriptTemplate) {
            presentation.isVisible = false
            return
        } else {
            presentation.isVisible = true
        }

        presentation.text = this.currentAction?.actionName ?: "Select Spring Web Context"
        this.currentAction?.icon?.let {  presentation.icon = it }
        presentation.isEnabledAndVisible = true
        (this.currentAction?.actionName)?.let {
            val hacSettings = RemoteConnectionUtil.getActiveRemoteConnectionSettings(project, RemoteConnectionType.Hybris)
            hacSettings.hacSpringWebContext = it
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

class ReloadWebContextAction(private val parent: GroovyChooseWebContextActionGroup) : AnAction("Reload Web Contexts", "Reloads the list of context", AllIcons.General.Refresh) {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = GroovyHACService.getInstance(project)
        service.loadWebContexts(e, parent.webContexts)
    }

}