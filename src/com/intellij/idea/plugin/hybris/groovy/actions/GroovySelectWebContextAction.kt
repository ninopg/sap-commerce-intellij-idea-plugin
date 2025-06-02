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

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.ui.MutableCollectionComboBoxModel
import com.jetbrains.rd.util.AtomicInteger

class GroovySelectWebContextAction : ComboBoxAction() {

    private val comboBoxModel = MutableCollectionComboBoxModel<String>()
    private val sequence = AtomicInteger(0)

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        updateComboBoxModel(listOf("Option ${sequence.incrementAndGet()}", "Option ${sequence.incrementAndGet()}", "Option ${sequence.incrementAndGet()}"))
   }

    init {
        updateComboBoxModel(listOf("Option ${sequence.incrementAndGet()}", "Option ${sequence.incrementAndGet()}", "Option ${sequence.incrementAndGet()}"))
    }

    private fun updateComboBoxModel(newItems: List<String>) {
        comboBoxModel.update(newItems)
    }

}