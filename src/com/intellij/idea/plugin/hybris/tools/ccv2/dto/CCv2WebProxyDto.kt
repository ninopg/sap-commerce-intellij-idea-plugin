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

package com.intellij.idea.plugin.hybris.tools.ccv2.dto

import com.intellij.idea.plugin.hybris.ccv1.model.WebProxyDTO

// REVIEWME
data class CCv2WebProxyDto(
    val code: String,
    val ipAddress: String?,
    val defaultDnsEntry: String?,
    val name: String? = null,
    val description: String? = null
) {

    // REVIEWME
    companion object {

        fun map(
            webProxy: WebProxyDTO
        ): CCv2WebProxyDto {
            return CCv2WebProxyDto(
                code = webProxy.code,
                ipAddress = webProxy.ipAddress,
                defaultDnsEntry = webProxy.defaultDnsEntry,
                name = webProxy.name,
                description = webProxy.name
            )
        }

    }

}