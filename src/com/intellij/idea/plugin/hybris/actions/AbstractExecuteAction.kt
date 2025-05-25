/*
 * This file is part of "SAP Commerce Developers Toolset" plugin for IntelliJ IDEA.
 * Copyright (C) 2019-2024 EPAM Systems <hybrisideaplugin@epam.com> and contributors
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

import com.intellij.idea.plugin.hybris.tools.remote.console.HybrisConsoleService
import com.intellij.idea.plugin.hybris.toolwindow.HybrisToolWindowFactory
import com.intellij.idea.plugin.hybris.toolwindow.HybrisToolWindowService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement

abstract class AbstractExecuteAction(
    internal val extension: String,
    private val consoleName: String
) : AnAction(), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    protected open fun doExecute(consoleService: HybrisConsoleService) {
        consoleService.executeStatement()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = CommonDataKeys.EDITOR.getData(e.dataContext) ?: return
        val project = e.project ?: return
        val psiFile = CommonDataKeys.PSI_FILE.getData(e.dataContext) ?: return

        val selectionModel = editor.selectionModel
        var content = selectionModel.selectedText
        if (content == null || content.trim { it <= ' ' }.isEmpty()) {
            content = editor.document.text
        }

        if (selectionModel.hasSelection() && psiFile is GroovyFile && !psiFile.importStatements.isEmpty()) {

            val document = editor.document
            val selectionStartLine = document.getLineNumber(selectionModel.selectionStart)
            val selectionEndLine = document.getLineNumber(selectionModel.selectionEnd)

            val missingImports = psiFile.importStatements.filter { import ->
                val importLine = document.getLineNumber(import.textOffset)
                importLine < selectionStartLine || importLine > selectionEndLine
            }

            val importStatements = missingImports.map { it.text }
            val importBlock = importStatements.joinToString(separator = "\n")
            content = "$importBlock\n\n$content"

        }

        with(HybrisToolWindowService.getInstance(project)) {
            activateToolWindow()
            activateToolWindowTab(HybrisToolWindowFactory.CONSOLES_ID)
        }

        val consoleService = HybrisConsoleService.getInstance(project)
        val console = consoleService.findConsole(consoleName)
        if (console == null) {
            LOG.warn("unable to find console $consoleName")
            return
        }
        consoleService.setActiveConsole(console)
        console.setInputText("/* ${psiFile.name} */\n$content")

        invokeLater {
            doExecute(consoleService)
        }
    }

    override fun update(e: AnActionEvent) {
        val file = e.dataContext.getData(CommonDataKeys.VIRTUAL_FILE)
        val enabled = file != null && file.name.endsWith(this.extension)
        e.presentation.isEnabledAndVisible = enabled
    }

    companion object {
        private val LOG = Logger.getInstance(AbstractExecuteAction::class.java)
    }
}
