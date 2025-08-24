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

package com.intellij.idea.plugin.hybris.toolwindow

import com.intellij.credentialStore.Credentials
import com.intellij.database.util.common.isNotNullOrEmpty
import com.intellij.icons.AllIcons
import com.intellij.idea.plugin.hybris.common.HybrisConstants
import com.intellij.idea.plugin.hybris.common.utils.HybrisIcons
import com.intellij.idea.plugin.hybris.tools.ccv2.CCv2Service
import com.intellij.idea.plugin.hybris.tools.ccv2.dto.CCv2EnvironmentDto
import com.intellij.idea.plugin.hybris.tools.ccv2.dto.CCv2EnvironmentStatus
import com.intellij.idea.plugin.hybris.tools.ccv2.settings.state.CCv2Subscription
import com.intellij.idea.plugin.hybris.tools.remote.RemoteConnectionScope
import com.intellij.idea.plugin.hybris.tools.remote.http.HybrisHacHttpClient
import com.intellij.idea.plugin.hybris.tools.remote.settings.RemoteConnectionListener
import com.intellij.idea.plugin.hybris.tools.remote.settings.state.RemoteConnectionSettingsState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ComboBoxPredicate
import com.intellij.ui.layout.selected
import java.awt.Component
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.swing.JComboBox

class RemoteHacConnectionDialog(
    project: Project,
    parentComponent: Component,
    settings: RemoteConnectionSettingsState
) : AbstractRemoteConnectionDialog(project, parentComponent, settings, "Remote SAP Commerce Instance") {

    protected lateinit var subscriptionComboBox: JComboBox<CCv2Subscription>
    protected lateinit var environmentComboBox: JComboBox<String>
    protected lateinit var serviceComboBox: JComboBox<String>

    private lateinit var sslProtocolComboBox: ComboBox<String>
    private lateinit var sessionCookieNameTextField: JBTextField

    override fun applyFields() {
        super.applyFields()

        project.messageBus.syncPublisher(RemoteConnectionListener.TOPIC).onHybrisConnectionModified(settings)
    }

    override fun createTestSettings() = with(RemoteConnectionSettingsState()) {
        type = settings.type
        hostIP = hostTextField.text
        port = portTextField.text
        isSsl = sslProtocolCheckBox.isSelected
        isWsl = isWslCheckBox?.isSelected ?: false
        sslProtocol = sslProtocolComboBox.selectedItem?.toString() ?: ""
        hacWebroot = webrootTextField.text
        sessionCookieName = sessionCookieNameTextField.text.takeIf { !it.isNullOrBlank() } ?: HybrisConstants.DEFAULT_SESSION_COOKIE_NAME
        credentials = Credentials(usernameTextField.text, String(passwordTextField.password))
        this
    }

    override fun testConnection(testSettings: RemoteConnectionSettingsState): String = HybrisHacHttpClient.getInstance(project)
        .testConnection(testSettings)

    override fun panel() = panel {

        row {
            label("Connection name:")
                .bold()
            connectionNameTextField = textField()
                .align(AlignX.FILL)
                .bindText(settings::displayName.toNonNullableProperty(""))
                .component
        }.layout(RowLayout.PARENT_GRID)

        row {
            label("Scope:")
                .comment("Non-personal settings will be stored in the <strong>hybrisProjectSettings.xml</strong> and can be shared via VCS.")
            comboBox(
                EnumComboBoxModel(RemoteConnectionScope::class.java),
                renderer = SimpleListCellRenderer.create("?") { it.title }
            )
                .bindItem(settings::scope.toNullableProperty(RemoteConnectionScope.PROJECT_PERSONAL))
        }.layout(RowLayout.PARENT_GRID)

        row {
            label("Subscription:").comment("Optional: Select a subscription to use for this connection.")
            subscriptionComboBox = comboBox(
                subscriptionsComboBoxModel,
                renderer = SimpleListCellRenderer.create { label, value, _ ->
                    if (value != null) {
                        label.icon = HybrisIcons.Module.CCV2
                        label.text = value.toString()
                    } else {
                        label.text = ""
                    }
                }
            )
            .widthGroup("topComboBoxes")
            .bindItem(
                 getter = { ->
                     (0 until subscriptionsComboBoxModel.size ).asSequence()
                         .map { subscriptionsComboBoxModel.getElementAt(it)}
                         .firstOrNull { it?.uuid == settings.subscription }
                 },
                 setter = { selected -> settings.subscription = selected?.uuid }
            )
            .onChanged {
                // FIXME
                environmentsComboBoxModel.removeAll()
                updateEnvironmentsComboBox()
                // updatePortTextField()
                // updateCredentials()
            }.component
            // FIXME
            // actionButton(ReloadEnvironmentsAction(subscriptionComboBox, this@RemoteHacConnectionDialog)).enabledIf(
            //     ComboBoxPredicate(subscriptionComboBox, { it != null })
            // )
        }.layout(RowLayout.PARENT_GRID)

        if (settings.subscription != null) {
            val semaphore = Semaphore(0)
            updateEnvironmentsComboBox(semaphore)
            val received = semaphore.tryAcquire(30, TimeUnit.SECONDS)
            if (!received && settings.environment.isNotNullOrEmpty) {
                environmentsComboBoxModel.add(settings.environment)
            }
        }

        row {
            label("Environment:").comment("Optional: Select an environment to use for this connection.")
            environmentComboBox = comboBox(
                environmentsComboBoxModel,
                renderer = SimpleListCellRenderer.create { label, value, _ ->
                    if (subscriptionComboBox.selectedItem != null && value != null) {
                        label.icon = HybrisIcons.Extension.CLOUD
                        // label.text = "${subscriptionComboBox.selectedItem}.${value}"
                        label.text = value
                    } else {
                        label.text = ""
                    }
                }
            )
            .widthGroup("topComboBoxes")
            .bindItem(settings::environment.toNullableProperty(defaultValue = ""))
            .enabledIf(ComboBoxPredicate(subscriptionComboBox, { it != null }))
            .onChanged {
                // updateHostsComboBox()
                // updateReplicaIdsComboBox()
                // updateCredentials()
            }
            .component
        }.layout(RowLayout.PARENT_GRID)

        row {
            label("Service:").comment("Optional: Select a service to use for this connection.")
            serviceComboBox = comboBox(
                listOf("") + commerceServices,
                renderer = SimpleListCellRenderer.create { label, value, _ ->
                    if (value?.isNotBlank() ?: false) {
                        label.icon = AllIcons.Nodes.Services
                        label.text = value
                    } else {
                        label.text = ""
                    }
                }
            )
            .widthGroup("topComboBoxes")
            .bindItem(settings::service.toNullableProperty(defaultValue = ""))
            .enabledIf(ComboBoxPredicate(environmentComboBox, { it.isNotNullOrEmpty }))
            .onChanged {
                // updateHostsComboBox()
                // updateReplicaIdsComboBox()
            }
            .component
        }.layout(RowLayout.PARENT_GRID)

        group("Full URL Preview", false) {
            row {
                urlPreviewLabel = label(settings.generatedURL)
                    .bold()
                    .align(AlignX.FILL)
                    .component
            }
            row {
                testConnectionLabel = label("")
                    .visible(false)
            }
            row {
                testConnectionComment = comment("")
                    .visible(false)
            }
        }

        group("Host Settings") {
            row {
                label("Address:")
                hostTextField = textField()
                    .comment("Host name or IP address")
                    .align(AlignX.FILL)
                    .bindText(settings::hostIP.toNonNullableProperty(HybrisConstants.DEFAULT_HOST_URL))
                    .onChanged { urlPreviewLabel.text = generateUrl() }
                    .addValidationRule("Address cannot be blank.") { it.text.isNullOrBlank() }
                    .component
            }.layout(RowLayout.PARENT_GRID)

            row {
                label("Port:")
                portTextField = textField()
                    .align(AlignX.FILL)
                    .bindText(settings::port.toNonNullableProperty(""))
                    .onChanged { urlPreviewLabel.text = generateUrl() }
                    .addValidationRule("Port should be blank or in a range of 1..65535.") {
                        if (it.text.isNullOrBlank()) return@addValidationRule false

                        val intValue = it.text.toIntOrNull() ?: return@addValidationRule true
                        return@addValidationRule intValue !in 1..65535
                    }
                    .component
            }.layout(RowLayout.PARENT_GRID)

            row {
                sslProtocolCheckBox = checkBox("SSL:")
                    .bindSelected(settings::isSsl)
                    .onChanged { urlPreviewLabel.text = generateUrl() }
                    .component
                sslProtocolComboBox = comboBox(
                    listOf(
                        "TLSv1",
                        "TLSv1.1",
                        "TLSv1.2"
                    ),
                    renderer = SimpleListCellRenderer.create("?") { it }
                )
                    .enabledIf(sslProtocolCheckBox.selected)
                    .bindItem(settings::sslProtocol.toNullableProperty())
                    .align(AlignX.FILL)
                    .component
            }.layout(RowLayout.PARENT_GRID)

            row {
                label("Webroot:")
                webrootTextField = textField()
                    .align(AlignX.FILL)
                    .bindText(settings::hacWebroot.toNonNullableProperty(""))
                    .onChanged { urlPreviewLabel.text = generateUrl() }
                    .component
            }.layout(RowLayout.PARENT_GRID)

            row {
                label("Session Cookie name:")
                sessionCookieNameTextField = textField()
                    .comment("Optional: override the session cookie name. Default is JSESSIONID.")
                    .align(AlignX.FILL)
                    .bindText(settings::sessionCookieName.toNonNullableProperty(HybrisConstants.DEFAULT_SESSION_COOKIE_NAME))
                    .apply { component.text = "" }
                    .component
            }.layout(RowLayout.PARENT_GRID)

            if (isWindows()) {
                wslHostConfiguration()
            }
        }

        group("Credentials") {
            row {
                label("Username:")
                usernameTextField = textField()
                    .align(AlignX.FILL)
                    .enabled(false)
                    .addValidationRule("Username cannot be blank.") { it.text.isNullOrBlank() }
                    .component
            }.layout(RowLayout.PARENT_GRID)

            row {
                label("Password:")
                passwordTextField = passwordField()
                    .align(AlignX.FILL)
                    .enabled(false)
                    .addValidationRule("Password cannot be blank.") { it.password.isEmpty() }
                    .component
            }.layout(RowLayout.PARENT_GRID)
        }
    }

    fun updateEnvironmentsComboBox(semaphore : Semaphore? = null) {
        if (subscriptionComboBox.selectedItem != null) {
            val subscription = subscriptionComboBox.selectedItem as CCv2Subscription
            environmentComboBox.isEnabled = false
            CCv2Service.getInstance(project).fetchEnvironments(
                listOf(subscription),
                { result : SortedMap<CCv2Subscription, Collection<CCv2EnvironmentDto>> ->
                    println(result)
                    // diableOtherComboBoxes(subscriptionComboBox)
                    result[subscription]?.let {
                        val environments = with(Regex("""([dsp])(\d+)""")) {
                            it.map { it.code }
                                .sortedWith(compareBy(
                                    { code -> if (matchEntire(code) != null) 0 else 1 },
                                    { code -> matchEntire(code)?.groupValues?.get(1)?.let { when (it) { "d" -> 0; "s" -> 1; "p" -> 2; else -> 3 } } ?: 3 },
                                    { code -> matchEntire(code)?.groupValues?.get(2)?.toIntOrNull() ?: Int.MAX_VALUE },
                                    { code -> code }
                                ))
                        }
                        environmentsComboBoxModel.update(environments)
                        environmentComboBox.isEnabled = true
                        semaphore?.release()
                    }
                },
                false,
                EnumSet.of(CCv2EnvironmentStatus.AVAILABLE),
                false,
                false,
                false
            )
        } else {
            settings.environment = ""
            environmentComboBox.selectedItem = ""
        }
    }

    companion object {
        val commerceServices = listOf("accstorefront", "api", "backoffice", "backgroundprocessing")
    }

}