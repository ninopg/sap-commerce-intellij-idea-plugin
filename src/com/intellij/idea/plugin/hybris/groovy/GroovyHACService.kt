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

package com.intellij.idea.plugin.hybris.groovy

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.idea.plugin.hybris.common.HybrisConstants
import com.intellij.idea.plugin.hybris.settings.components.DeveloperSettingsComponent
import com.intellij.idea.plugin.hybris.tools.remote.RemoteConnectionType
import com.intellij.idea.plugin.hybris.tools.remote.RemoteConnectionUtil.getActiveRemoteConnectionSettings
import com.intellij.idea.plugin.hybris.tools.remote.http.HybrisHacHttpClient
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManagerImpl
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicElementSettings

private const val GHAC_SPRING_BEANS_GROOVY = "/ghac/springBeans.groovy"

data class WebApplicationContext(
    val contextId: String,
    val id: String,
    val applicationName: String,
    val displayName: String,
    val type: String,
    val isRoot: Boolean,
    val parent: ParentContext?,
    val beanDefinitions: List<BeanDefinition>?,
    val aliases: Map<String,String>?
)

data class ParentContext(
    val contextId: String,
    val id: String,
    val applicationName: String,
    val displayName: String,
    val type: String,
    val isRoot: Boolean
)

data class BeanDefinition(
    val name: String,
    val beanDefinitionType: String,
    val type: String?,
    val abstract: Boolean,
    val parent: String?,
    val scope: String?,
    val singleton: Boolean,
    val prototype: Boolean,
    val path: String,
    val aliases: List<String>?
)

@Service(Service.Level.PROJECT)
class GroovyHACService(val project: Project, private val coroutineScope: CoroutineScope) {

    private val groovySettings = DeveloperSettingsComponent.getInstance(project).state.groovySettings

    fun loadBeanDefinitions(event: AnActionEvent) {
        val project = event.project
        if (project != null) {
            coroutineScope.launch {
                doLoadBeanDefinitions(project)
            }
        }
    }

    fun loadWebContexts(event: AnActionEvent, webContexts: MutableList<String>) {
        val project = event.project
        if (project != null) {
            coroutineScope.launch {
                doLoadWebContexts(project, webContexts)
            }
        }
    }

    private suspend fun doLoadBeanDefinitions(project: Project) {

        var success = false
        var resultMessage = "Task completed"

        val windowManager = WindowManager.getInstance().getStatusBar(project)
        val hacClient = HybrisHacHttpClient.getInstance(project)

        withBackgroundProgress(project, "Loading Spring bean definitions from hAC", cancellable = true) {

            try {

                reportProgress(100) { progressReporter ->

                    val script = hacClient.getScriptAsResource(GHAC_SPRING_BEANS_GROOVY)

                    if (script != null) {

                        var beanCounter1 = 0
                        var beanCounter2 = 0

                        try {

                            val hacConnectionSettings = getActiveRemoteConnectionSettings(project, RemoteConnectionType.Hybris)
                            val springWebContext = hacConnectionSettings.hacSpringWebContext ?: "default"
                            val httpResponse = hacClient.executeGroovyScript(project, script, false, 30_000, springWebContext, HybrisHacHttpClient.GHAC_SCRIPT_TEMPLATE_GROOVY)
                            if (httpResponse.hasError()) {
                                success = false
                                resultMessage = httpResponse.errorMessage
                            } else if (httpResponse.result?.isBlank() ?: true) {
                                success = false
                                resultMessage = "Empty response from hac for script $GHAC_SPRING_BEANS_GROOVY"
                            } else {

                                val baseClass = "groovy.lang.Script"
                                val gson = Gson()
                                val type = object : TypeToken<Map<String, WebApplicationContext>>() {}.type
                                val contextMap: Map<String, WebApplicationContext> = gson.fromJson(httpResponse.result, type)

                                val dynamicManager = DynamicManagerImpl.getInstance(project)
                                val existingPropsMap = dynamicManager
                                    .findDynamicPropertiesOfClass(baseClass).associateBy({ it.name }, { it.type })

                                val totContexts = contextMap.size
                                var currentContext = 1

                                for ((key, context) in contextMap.entries) {

                                    LOG.info("processing bean definitions for spring context $key")

                                    progressReporter.sizedStep(100 * currentContext / totContexts, "Processing bean definitions for spring context $key") {

                                        val beansMap = (context.beanDefinitions)?.associateBy { it.name } ?: emptyMap()

                                        for (bean in beansMap.values) {
                                            if (skipBean(bean)) continue

                                            val beanNames : List<String> =
                                                if (!bean.aliases.isNullOrEmpty() && groovySettings.onlyRegisterAliases)
                                                    bean.aliases
                                                else
                                                    listOf(bean.name) + (bean.aliases ?: emptyList())

                                            for (beanName in beanNames) {
                                                if (!existingPropsMap.containsKey(beanName)) {
                                                    val prop = DynamicElementSettings()
                                                    prop.containingClassName = baseClass
                                                    prop.name = beanName
                                                    prop.type = bean.type
                                                    prop.isMethod = false
                                                    prop.isStatic = false
                                                    dynamicManager.addProperty(prop)
                                                    beanCounter1++
                                                } else if (existingPropsMap.containsKey(beanName) && existingPropsMap.get(beanName) != bean.type) {
                                                    dynamicManager.replaceDynamicPropertyType(baseClass, beanName, existingPropsMap.get(beanName), bean.type)
                                                    beanCounter1++
                                                }
                                            }
                                            beanCounter2++
                                        }

                                        /*
                                        LOG.info("processing aliases for spring context $key")

                                        for ((aliasName, beanName) in context.aliases ?: emptyMap()) {
                                            val targetBean = beansMap[beanName]
                                            if (targetBean != null) {
                                                if (skipBean(targetBean)) continue
                                                val beanType = targetBean.type ?: "java.lang.Object"
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
                                            } else {
                                                LOG.error("$aliasName in referencing invalid $beanName")
                                            }
                                            beanCounter2++
                                        }
                                        */

                                    }

                                    currentContext++

                                }

                                dynamicManager.fireChange()

                                LOG.info("done: updated/added: $beanCounter1, found: $beanCounter2")

                                success = true
                                resultMessage = "$beanCounter1/$beanCounter2 bean definitions registered/found."

                            }

                        } catch (e: Exception) {
                            val errorMessage = "Error loading beans ${e.message}"
                            success = false
                            windowManager.info = errorMessage
                            resultMessage = errorMessage
                            LOG.error("Error loading beans", e)
                        }

                    } else {
                        val errorMessage = "Script $GHAC_SPRING_BEANS_GROOVY not found"
                        success = false
                        windowManager.info = errorMessage
                        resultMessage = errorMessage
                        LOG.error(errorMessage)
                    }

                }

            } catch (e: ProcessCanceledException) {
                success = false
                resultMessage = "Task canceled"
                throw e
            } catch (e: Exception) {
                success = false
                resultMessage = "Task failed: ${e.message}"
                throw RuntimeException(e)
            } finally {
                showNotification(project, "Spring Beans Definitions", resultMessage, success)
            }

        }

    }

    private fun showNotification(project: Project, title: String, message: String, isSuccess: Boolean) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(HybrisConstants.NOTIFICATION_GROUP_HYBRIS)
            .createNotification(
                title, message,
                if (isSuccess) NotificationType.INFORMATION else NotificationType.ERROR
            )
            .notify(project)
    }

    private fun skipBean(bd: BeanDefinition): Boolean {
        if (groovySettings.hacBeansExclusionList.any { bd.type != null && bd.type.startsWith(it) }) return true
        if (bd.abstract) return true
        if (bd.prototype) return true
        // if (bd.aliases != null && bd.aliases.isNotEmpty()) return true
        return false
    }

    private suspend fun doLoadWebContexts(project: Project, webContexts: MutableList<String>) {

        var success = false
        var resultMessage = "Task completed"

        // val windowManager = WindowManager.getInstance().getStatusBar(project)
        reportProgress(1) { progressReporter ->
            progressReporter.sizedStep(1, "Loading Spring Web Contexts...") {
                try {
                    val hacClient = HybrisHacHttpClient.getInstance(project)
                    val baseScript = "springWeb.keySet().join('|')"
                    val response = hacClient.executeGroovyScript(project, baseScript, false, 10_000, "default", HybrisHacHttpClient.GHAC_SCRIPT_TEMPLATE_GROOVY)
                    if (response.hasError()) {
                        resultMessage = response.errorMessage
                        success = false
                    } else {
                        webContexts.clear()
                        webContexts.add("default")
                        val contexts = response.result.split("|").filter { it.isNotBlank() }.sorted()
                        webContexts.addAll(contexts)
                        val settings = getActiveRemoteConnectionSettings(project, RemoteConnectionType.Hybris)
                        resultMessage = "Found ${contexts.size} contexts for ${settings.hostIP}"
                        success = true
                    }
                } catch (e: Exception) {
                    success = false
                    resultMessage = "Task failed: ${e.message}"
                } finally {
                    showNotification(project, "Spring Web Contexts", resultMessage, success)
                }
            }
        }

    }

    companion object {
        fun getInstance(project: Project): GroovyHACService = project.getService(GroovyHACService::class.java)
        private val LOG = Logger.getInstance(GroovyHACService::class.java)
    }

}