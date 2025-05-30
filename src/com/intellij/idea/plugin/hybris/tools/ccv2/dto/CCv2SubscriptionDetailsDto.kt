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

import com.intellij.idea.plugin.hybris.ccv1.model.SubscriptionDetailDTO
import com.intellij.idea.plugin.hybris.settings.CCv2Subscription

// REVIEWME
data class CCv2SubscriptionDetailsDto (
    val code: String,
    val customerCode: String,
    val externalCode: String,
    val name: String? = null,
    val customerName: String? = null,
    val status: String? = null,
    val regionCode: String? = null,
    val regionName: String? = null,
    val internal: Boolean? = null
) : CCv2DTO, Comparable<CCv2SubscriptionDetailsDto> {

    override fun compareTo(other: CCv2SubscriptionDetailsDto): Int = this.code.compareTo(other.code)

    companion object {

        fun map(
            subscription: SubscriptionDetailDTO
        ): CCv2SubscriptionDetailsDto {
            return CCv2SubscriptionDetailsDto(
                code = subscription.code,
                customerCode = subscription.customerCode,
                externalCode = subscription.externalCode,
                name = subscription.name,
                customerName = subscription.customerName,
                status = subscription.status,
                regionCode = subscription.regionCode,
                regionName = subscription.regionName,
                internal = subscription.internal
            )
        }

    }

}