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

import com.google.gson.Gson
import com.intellij.idea.plugin.hybris.common.utils.HybrisIcons
import com.intellij.idea.plugin.hybris.tools.remote.http.HybrisHacHttpClient
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManagerImpl
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicElementSettings

class GroovyGenerateDynamicPropertiesAction: AnAction(
    "Generate Dynamic Properties",
    null,
    HybrisIcons.SPRING_BEAN
) {

    override fun actionPerformed(e: AnActionEvent) {

        // REVIEWME: for now we are registering all beans all toghether

        val project = e.project ?: return
        val hacClient = HybrisHacHttpClient.getInstance(project)
        val script = hacClient.getScript("/ghac/springBeans.groovy")
        if (script != null) {

            val httpResponse = hacClient.executeGroovyScript(project, script, false, 30_000, "default")
            if (httpResponse.hasError() || httpResponse.result?.isBlank() ?: true) {
                // TODO: handle error case
                return
            }

            try {

                // val json: Map<String, Any?> = kotlinx.serialization.json.Json.decodeFromString(httpResponse.result)

                val baseClass = "groovy.lang.Script"
                val gson = Gson()
                val json: Map<String, Any?> = gson.fromJson(httpResponse.result, Map::class.java) as Map<String, Any?>

                val dynamicManager = DynamicManagerImpl.getInstance(project)
                val existingPropsMap = dynamicManager
                    .findDynamicPropertiesOfClass(baseClass).associateBy({ it.name }, { it.type })

                var beanCounter1 = 0
                var beanCounter2 = 0

                for ((key, value) in json.entries) {

                    val context = json.get(key) as Map<String, Any?>

                    val beansMap = (context.get("beanDefinitions") as List<Map<String, Any?>>).associateBy { it["name"] as? String }

                    for ((beanName, bean) in beansMap) {
                        if (skipBean(bean)) continue
                        val beanType = bean["type"] as? String ?: "java.lang.Object"
                        if (!existingPropsMap.containsKey(beanName)) {
                            val prop = DynamicElementSettings()
                            prop.containingClassName = baseClass
                            prop.name = beanName
                            prop.type = beanType
                            prop.isMethod = false
                            prop.isStatic = false
                            dynamicManager.addProperty(prop)
                            beanCounter1++
                        } else if (existingPropsMap.containsKey(beanName) && existingPropsMap.get(beanName) != beanType) {
                            dynamicManager.replaceDynamicPropertyType(baseClass, beanName, existingPropsMap.get(beanName), beanType)
                            beanCounter1++
                        }
                        beanCounter2++
                    }

                    val aliases = context.get("aliases") as Map<String, String>

                    for ((aliasName, beanName) in aliases) {
                        val targetBean = beansMap[aliasName] as Map<String, Any?>?
                        if (skipBean(targetBean)) continue
                        val beanType = targetBean!!["type"] as? String ?: "java.lang.Object"
                        if (!existingPropsMap.containsKey(beanName)) {
                            val prop = DynamicElementSettings()
                            prop.containingClassName = baseClass
                            prop.name = aliasName
                            prop.type = beanType
                            prop.isMethod = false
                            prop.isStatic = false
                            dynamicManager.addProperty(prop)
                            beanCounter1++
                        } else if (existingPropsMap.containsKey(aliasName) && existingPropsMap.get(aliasName) != beanType) {
                            dynamicManager.replaceDynamicPropertyType(baseClass, aliasName, existingPropsMap.get(aliasName), beanType)
                            beanCounter1++
                        }
                        beanCounter2++
                    }

                }

                dynamicManager.fireChange()

                System.out.println("done: ${beanCounter1}/${beanCounter2}")

            } catch (e: Exception) {
                e.printStackTrace()
            }

        } else {
            // TODO: handle error case
        }
    }

    private fun skipBean(bean: Map<String, Any?>?): Boolean {
        if (bean == null) return  true
        if (bean["name"] == null) return true
        if (bean["abstract"].let{ it == true }) return true
        // if (bean["scope"] != "singleton") return true
        if (bean["prototype"].let{ it == true }) return true
        return false
    }

}