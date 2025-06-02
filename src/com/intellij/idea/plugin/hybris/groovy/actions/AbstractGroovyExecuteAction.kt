/*
 * This file is part of "SAP Commerce Developers Toolset" plugin for IntelliJ IDEA.
 * Copyright (C) 2014-2016 Alexander Bartash <AlexanderBartash@gmail.com>
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
package com.intellij.idea.plugin.hybris.groovy.actions

import com.intellij.idea.plugin.hybris.actions.AbstractExecuteAction
import com.intellij.idea.plugin.hybris.common.HybrisConstants
import com.intellij.idea.plugin.hybris.settings.components.DeveloperSettingsComponent
import com.intellij.idea.plugin.hybris.tools.remote.RemoteConnectionType
import com.intellij.idea.plugin.hybris.tools.remote.RemoteConnectionUtil
import com.intellij.idea.plugin.hybris.tools.remote.console.HybrisConsoleService
import com.intellij.idea.plugin.hybris.tools.remote.console.impl.HybrisGroovyConsole
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.asSafely
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.swing.Icon

abstract class AbstractGroovyExecuteAction(controlText: String, controlDescription: String, controlIcon: Icon, private val commitMode: Boolean) : AbstractExecuteAction(
    GroovyFileType.GROOVY_FILE_TYPE.defaultExtension,
    HybrisConstants.CONSOLE_TITLE_GROOVY
) {
    init {
        with(templatePresentation) {
            text = controlText
            description = controlDescription
            icon = controlIcon
        }
    }

    override fun doExecute(consoleService: HybrisConsoleService) {
        consoleService.getActiveConsole()
            ?.asSafely<HybrisGroovyConsole>()
            ?.also {
                it.updateCommitMode(commitMode)
                super.doExecute(consoleService)
            }
    }

    override fun preprocessContent(project: Project, editor: Editor, e: AnActionEvent, content: String): String {

        val psiFile = CommonDataKeys.PSI_FILE.getData(e.dataContext) ?: return content

        val selectionModel = editor.selectionModel

        var processedContent = content

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
            processedContent = "$importBlock\n\n$content"

        }

        // val undefinedVariables = analyzeCode(project,processedContent)

        val settings = DeveloperSettingsComponent.getInstance(project).state

        if (!settings.groovySettings.disableScriptTemplate) {

            val hacConnectionSettings = RemoteConnectionUtil.getActiveRemoteConnectionSettings(project, RemoteConnectionType.Hybris)

            // read a resource file as a string
            val templateStream = javaClass.getResourceAsStream("/ghac/scriptTemplate.groovy")
            val template = templateStream?.bufferedReader()?.use { it.readText() } ?: ""
            processedContent = template
                .replace("\$hacEncodedScript", String(Base64.getEncoder().encode(content.toByteArray(StandardCharsets.UTF_8)), StandardCharsets.UTF_8))
                .replace("\$hacSpringWebContext", hacConnectionSettings.hacSpringWebContext ?: "default")

        }

        processedContent = "/* ${psiFile.name} */\n$processedContent"

        return processedContent

    }

    private fun analyzeCode(project: Project, code: String): List<String> {

        val undefinedVariables = mutableListOf<String>()

        // groovy.lang.GroovyCodeSource
        // org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment extends GroovyFileImpl
        // org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock

        // Create a temporary Groovy PSI file
        val factory = GroovyPsiElementFactory.getInstance(project)
        val tempFile = factory.createGroovyFile("hacTemp.groovy", false, GroovyCodeFragment(project,code))

        // Find all reference expressions (variable usages)
        val references = PsiTreeUtil.findChildrenOfType(tempFile, GrReferenceExpression::class.java)
            .filter { !it.isQualified } // Exclude qualified references (e.g., obj.field)

        for (ref in references) {
            val varName = ref.referenceName ?: continue

            // Check if the variable is resolved
            val resolved = ref.resolve()
            if (resolved == null) {
                // Check if the variable is defined locally
                var isDefined = false
                var parent: PsiElement? = ref.parent
                while (parent != null && parent != tempFile) {
                    if (parent is com.intellij.psi.PsiVariable && parent.name == varName) {
                        isDefined = true
                        break
                    }
                    parent = parent.parent
                }
                if (!isDefined) {
                    undefinedVariables.add(varName)
                }
            }
        }

        return undefinedVariables

    }

}