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
import com.intellij.idea.plugin.hybris.tools.ccv2.dto.CCv2DeploymentStatusEnum
import com.intellij.idea.plugin.hybris.tools.ccv2.dto.CCv2EnvironmentDto
import com.intellij.idea.plugin.hybris.tools.ccv2.dto.CCv2EnvironmentStatus
import com.intellij.idea.plugin.hybris.tools.ccv2.dto.CCv2EnvironmentType
import com.intellij.idea.plugin.hybris.tools.ccv2.dto.CCv2ServiceDto
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
    protected lateinit var environmentComboBox: JComboBox<CCv2EnvironmentDto>
    protected lateinit var serviceComboBox: JComboBox<CCv2ServiceDto>

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
                         .firstOrNull { it?.uuid == settings.subscriptionUUID }
                 },
                 setter = { selected -> settings.subscriptionUUID = selected?.uuid }
            )
            .onChanged {
                settings.environmentCode = null
                settings.serviceCode = null
                updateEnvironmentsComboBox()
                // updateServicesComboBox()
            }.component
            // FIXME
            // actionButton(ReloadEnvironmentsAction(subscriptionComboBox, this@RemoteHacConnectionDialog)).enabledIf(
            //     ComboBoxPredicate(subscriptionComboBox, { it != null })
            // )
        }.layout(RowLayout.PARENT_GRID)

        row {
            label("Environment:").comment("Optional: Select an environment to use for this connection.")
            environmentComboBox = comboBox(
                environmentsComboBoxModel,
                renderer = SimpleListCellRenderer.create { label, value, _ ->
                    if (subscriptionComboBox.selectedItem != null && value != null) {
                        label.icon = value.type.icon
                        label.text = "${value.code}${value.name.takeIf { it.isNotNullOrEmpty && it != value.code }?.let{ " - $it" } ?: ""}"
                    } else {
                        label.text = ""
                    }
                }
            )
            .widthGroup("topComboBoxes")
            .bindItem(
                getter = { ->
                    (0 until environmentsComboBoxModel.size ).asSequence()
                        .map { environmentsComboBoxModel.getElementAt(it)}
                        .firstOrNull { it?.code == settings.environmentCode }
                },
                setter = { selected -> settings.environmentCode = selected?.code }
            )
            .enabledIf(ComboBoxPredicate(subscriptionComboBox, { it != null }))
            .onChanged {
                settings.serviceCode = null
                updateServicesComboBox()
            }
            .component
        }.layout(RowLayout.PARENT_GRID)

        row {
            label("Service:").comment("Optional: Select a service to use for this connection.")
            serviceComboBox = comboBox(
                servicesComboBoxModel,
                renderer = SimpleListCellRenderer.create { label, value, _ ->
                    if (subscriptionComboBox.selectedItem != null && environmentComboBox.selectedItem != null && value != null) {
                        label.icon = AllIcons.Nodes.Services
                        label.text = value.code.removePrefix("hcs_platform_")
                    } else {
                        label.text = ""
                    }
                }
            )
            .widthGroup("topComboBoxes")
            .bindItem(
                getter = { ->
                    (0 until servicesComboBoxModel.size ).asSequence()
                        .map { servicesComboBoxModel.getElementAt(it)}
                        .firstOrNull { it?.code == settings.serviceCode }
                },
                setter = { selected -> settings.serviceCode = selected?.code }
            )
            .enabledIf(ComboBoxPredicate(environmentComboBox, { it != null }))
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

        if (settings.subscriptionUUID != null) {
            val semaphore = Semaphore(0)
            updateEnvironmentsComboBox(semaphore)
            val received = semaphore.tryAcquire(30, TimeUnit.SECONDS)
            if (!received) {
                settings.environmentCode?.let {
                    val environment = CCv2EnvironmentDto(
                        code = it,
                        name = it,
                        type = CCv2EnvironmentType.UNKNOWN,
                        status = CCv2EnvironmentStatus.UNKNOWN,
                        deploymentStatus = CCv2DeploymentStatusEnum.UNKNOWN,
                        mediaStorages = emptyList(),
                        link = null
                    )
                    environmentsComboBoxModel.add(listOf(environment))
                }
            }
        }

        if (settings.environmentCode != null) {
            val semaphore = Semaphore(0)
            updateServicesComboBox(semaphore)
            val received = semaphore.tryAcquire(30, TimeUnit.SECONDS)
            if (!received) {
                settings.serviceCode?.let {
                    // FIXME
                }
            }
        }

    }

    fun updateEnvironmentsComboBox(semaphore : Semaphore? = null) {
        try {
            environmentsComboBoxModel.removeAll()
            if (subscriptionComboBox.selectedItem != null) {
                val subscription = subscriptionComboBox.selectedItem as CCv2Subscription
                environmentComboBox.isEnabled = false
                CCv2Service.getInstance(project).fetchEnvironments(
                    listOf(subscription),
                    { result: SortedMap<CCv2Subscription, Collection<CCv2EnvironmentDto>> ->
                        val sortedEnvironments = result[subscription]?.toList()?.sortedBy { it.order } ?: emptyList()
                        environmentsComboBoxModel.update(sortedEnvironments)
                        if (settings.environmentCode != null) {
                            sortedEnvironments.find { it.code == settings.environmentCode }?.let {
                                environmentsComboBoxModel.selectedItem = it
                            }
                        }
                        environmentComboBox.isEnabled = true
                    },
                    false,
                    EnumSet.of(CCv2EnvironmentStatus.AVAILABLE),
                    false,
                    false,
                    false
                )
            } else {
                // settings.environmentCode = ""
                // environmentComboBox.selectedItem = ""
            }
        } finally {
            semaphore?.release()
        }
    }

    fun updateServicesComboBox(semaphore : Semaphore? = null) {
        try {
            servicesComboBoxModel.removeAll()
            if (environmentComboBox.selectedItem != null && environmentComboBox.selectedItem != null) {
                val subscription = subscriptionComboBox.selectedItem as CCv2Subscription
                val environment = environmentsComboBoxModel.selectedItem as CCv2EnvironmentDto
                serviceComboBox.isEnabled = false
                CCv2Service.getInstance(project).fetchEnvironmentServices(
                    subscription,
                    environment
                ) { result: Collection<CCv2ServiceDto>? ->
                    val services = result?.toList()?.filter { (it.availableReplicas ?: 0) > 0 && it.code.startsWith("hcs_platform_") } ?: emptyList()
                    servicesComboBoxModel.update(services)
                    if (settings.serviceCode != null) {
                        services.find { it.code == settings.serviceCode }?.let {
                            servicesComboBoxModel.selectedItem = it
                        }
                    }
                    serviceComboBox.isEnabled = true
                }
            } else {
                // settings.environmentCode = ""
                // environmentComboBox.selectedItem = ""
            }
        } finally {
            semaphore?.release()
        }
    }

}