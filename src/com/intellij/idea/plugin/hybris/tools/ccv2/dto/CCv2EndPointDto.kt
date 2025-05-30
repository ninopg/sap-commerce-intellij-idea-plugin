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

import com.intellij.idea.plugin.hybris.ccv1.model.EndPointDTO

// REVIEWME
data class CCv2EndPointDto(
    val code: String,
    val domainName: String,
    val k8sService: String,
    val name: String? = null,
    val description: String? = null,
    val priority: Int? = null,
    val protocol: String? = null,
    val webProxy: String? = null
)  {

    companion object {

        fun map(
            endPoint: EndPointDTO
        ): CCv2EndPointDto {
            return CCv2EndPointDto(
                code = endPoint.code,
                domainName = endPoint.domainName,
                k8sService = endPoint.k8sService,
                name = endPoint.name,
                description = endPoint.name,
                priority = endPoint.priority,
                protocol = endPoint.protocol,
                webProxy = endPoint.webProxy
            )
        }

    }

}