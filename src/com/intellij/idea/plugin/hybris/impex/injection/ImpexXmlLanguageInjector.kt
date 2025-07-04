/*
 * This file is part of "SAP Commerce Developers Toolset" plugin for IntelliJ IDEA.
 * Copyright (C) 2014-2016 Alexander Bartash <AlexanderBartash@gmail.com>
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
package com.intellij.idea.plugin.hybris.impex.injection

import com.intellij.idea.plugin.hybris.impex.psi.impl.ImpexStringImpl
import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.InjectedLanguagePlaces
import com.intellij.psi.LanguageInjector
import com.intellij.psi.PsiLanguageInjectionHost


class ImpexXmlLanguageInjector : LanguageInjector {


    override fun getLanguagesToInject(
        host: PsiLanguageInjectionHost,
        injectionPlacesRegistrar: InjectedLanguagePlaces
    ) {
        if (host !is ImpexStringImpl) return

        val hostString = StringUtil.unquoteString(host.getText()).lowercase()
        if (StringUtil.trim(hostString).replaceFirst("\"", "").isXmlLike()) {
            val language = XMLLanguage.INSTANCE
            injectionPlacesRegistrar.addPlace(
                language,
                TextRange.from(QUOTE_SYMBOL_LENGTH, host.getTextLength() - 2), null, null
            )
        }
    }

    companion object {
        private const val QUOTE_SYMBOL_LENGTH = 1
        private val LOG = Logger.getInstance(ImpexXmlLanguageInjector::class.java)
    }


    /**
     * return true if the String passed in is something like XML
     *
     *
     * @return true of the string is XML, false otherwise
     */
    private val xmlPatternRegExp = "<(\\S+?)(.*?)>(.*?)</\\1>".toRegex()

    private fun String.isXmlLike(): Boolean {
        if (this.trim { it <= ' ' }.isNotEmpty()) {
            if (this.trim { it <= ' ' }.startsWith("<")) {
                return xmlPatternRegExp.containsMatchIn(this)
            }
        }

        return false
    }
}