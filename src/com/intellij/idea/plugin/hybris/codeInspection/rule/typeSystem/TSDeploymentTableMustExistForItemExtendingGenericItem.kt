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

package com.intellij.idea.plugin.hybris.codeInspection.rule.typeSystem

import com.intellij.idea.plugin.hybris.codeInspection.fix.xml.XmlAddTagQuickFix
import com.intellij.idea.plugin.hybris.common.HybrisConstants
import com.intellij.idea.plugin.hybris.system.type.meta.TSMetaModelAccess
import com.intellij.idea.plugin.hybris.system.type.meta.TSMetaModelStateService
import com.intellij.idea.plugin.hybris.system.type.model.Deployment
import com.intellij.idea.plugin.hybris.system.type.model.ItemType
import com.intellij.idea.plugin.hybris.system.type.model.Items
import com.intellij.idea.plugin.hybris.system.type.model.all
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder
import com.intellij.util.xml.highlighting.DomHighlightingHelper
import org.apache.commons.lang3.StringUtils

class TSDeploymentTableMustExistForItemExtendingGenericItem : AbstractTSInspection() {

    override fun inspect(
        project: Project,
        dom: Items,
        holder: DomElementAnnotationHolder,
        helper: DomHighlightingHelper,
        severity: HighlightSeverity
    ) {
        dom.itemTypes.all.forEach { check(it, project, holder, severity) }
    }

    private fun check(
        dom: ItemType,
        project: Project,
        holder: DomElementAnnotationHolder,
        severity: HighlightSeverity
    ) {
        // skip non-ComposedType
        if (HybrisConstants.TS_META_VIEW_TYPE.equals(dom.metaType.stringValue, true) || HybrisConstants.TS_COMPOSED_TYPE.equals(dom.metaType.stringValue, true)) return

        val itemTypeCode = dom.code.stringValue ?: return

        val metaItem = project.service<TSMetaModelStateService>().get().getMetaItem(itemTypeCode)
            ?: return

        if (StringUtils.isNotBlank(metaItem.deployment?.typeCode)) return

        val otherDeclarationsWithDeploymentTable = metaItem.declarations.any { StringUtils.isNotBlank(it.deployment?.typeCode) }

        if (otherDeclarationsWithDeploymentTable) return

        val otherDeclarationsMarkedAsAbstract = metaItem.declarations.any { it.isAbstract }

        if (metaItem.isAbstract || otherDeclarationsMarkedAsAbstract) return

        val allExtends = metaItem.allExtends.flatMap { it.declarations }

        // skip Descriptor declarations
        if (allExtends.count { "Descriptor".equals(it.name, true) } > 0) return

        val countDeploymentTablesInParents = allExtends
            .count { StringUtils.isNotBlank(it.deployment?.typeCode) }

        if (countDeploymentTablesInParents > 0) return

        val fix = getOptionalFix(project, itemTypeCode)

        if (fix == null) {
            holder.createProblem(dom, severity, displayName, getTextRange(dom))
        } else {
            holder.createProblem(dom, severity, displayName, getTextRange(dom), fix)
        }
    }

    private fun getOptionalFix(project: Project, itemTypeCode: String) = TSMetaModelAccess.getInstance(project).getNextAvailableTypeCode()
        ?.toString()
        ?.let {
            sortedMapOf(
                Deployment.TABLE to itemTypeCode,
                Deployment.TYPE_CODE to it,
            )
        }
        ?.let {
            XmlAddTagQuickFix(
                tagName = ItemType.DEPLOYMENT,
                insertAfterTag = ItemType.DESCRIPTION,
                attributes = it
            )
        }
}