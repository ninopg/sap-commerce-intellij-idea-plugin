/*
 * This file is part of "SAP Commerce Developers Toolset" plugin for IntelliJ IDEA.
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

package com.intellij.idea.plugin.hybris.toolwindow

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.database.util.common.isNotNullOrEmpty
import com.intellij.icons.AllIcons
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.idea.plugin.hybris.common.HybrisConstants
import com.intellij.idea.plugin.hybris.common.utils.HybrisIcons
import com.intellij.idea.plugin.hybris.settings.CCv2Subscription
import com.intellij.idea.plugin.hybris.settings.RemoteConnectionSettings
import com.intellij.idea.plugin.hybris.tools.ccv2.CCv2Service
import com.intellij.idea.plugin.hybris.tools.remote.RemoteConnectionScope
import com.intellij.idea.plugin.hybris.tools.remote.http.HybrisHacHttpClient
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ComboBoxPredicate
import com.intellij.ui.layout.selected
import org.jetbrains.annotations.NotNull
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JComboBox


class ReloadEnvironmentsAction(val subscriptionComboBox: JComboBox<CCv2Subscription>, val dialog: RemoteHacConnectionDialog) :
    AnAction(
        "Reload Environments",
        "Reloads environments for the selected) subscription",
        AllIcons.General.Refresh) {
    override fun actionPerformed(e: AnActionEvent) {
        if (subscriptionComboBox.selectedItem != null) {
            dialog.updateEnvironmentsComboBox()
            dialog.updateHostsComboBox()
            dialog.updateReplicaIdsComboBox()
            dialog.updateCredentials()
        }
    }

    override fun update(@NotNull e: AnActionEvent) {
        e.presentation.setEnabled(subscriptionComboBox.selectedItem != null)
    }

}

class ShowPasswordAction (val passwordTextField: JBPasswordField) :
    ToggleAction(
        "Show Password",
        "Toggle password visibility",
        AllIcons.General.Show) {
    override fun isSelected(e: AnActionEvent): Boolean = passwordTextField.echoChar == 0.toChar()

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (!isSelected(e)) {
            passwordTextField.setEchoChar(0.toChar())
        } else {
            passwordTextField.setEchoChar('*')
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

}

// REVIEWME: all the logic to init, update needs to be reviewed
class RemoteHacConnectionDialog(
    project: Project,
    parentComponent: Component,
    settings: RemoteConnectionSettings,
    environmentsComboBoxModel: MutableCollectionComboBoxModel<String> = MutableCollectionComboBoxModel<String>(),
    hostsComboBoxModel: MutableCollectionComboBoxModel<String> = MutableCollectionComboBoxModel<String>(),
    replicaIdsComboBoxModel: MutableCollectionComboBoxModel<String> = MutableCollectionComboBoxModel<String>()
) : AbstractRemoteConnectionDialog(
    project, parentComponent, settings, "Remote SAP Commerce Instance",
    environmentsComboBoxModel = environmentsComboBoxModel,
    hostsComboBoxModel = hostsComboBoxModel,
    replicaIdsComboBoxModel = replicaIdsComboBoxModel
) {

    private lateinit var sslProtocolComboBox: ComboBox<String>
    private lateinit var sessionCookieNameTextField: JBTextField

    override fun createTestSettings() = with(RemoteConnectionSettings()) {
        type = settings.type
        hostIP = hostEditableComboBox.selectedItem?.toString()
        port = portTextField.text
        isSsl = sslProtocolCheckBox.isSelected
        isWsl = isWslCheckBox?.isSelected ?: false
        sslProtocol = sslProtocolComboBox.selectedItem?.toString() ?: ""
        hacWebroot = webrootTextField.text
        sessionCookieName = sessionCookieNameTextField.text.takeIf { !it.isNullOrBlank() } ?: HybrisConstants.DEFAULT_SESSION_COOKIE_NAME
        replicaId = replicaIdComboBox.selectedItem?.toString()?.takeIf { !it.isNullOrBlank() } ?: ""
        credentials = Credentials(usernameTextField.text, String(passwordTextField.password))
        this
    }

    override fun testConnection(testSettings: RemoteConnectionSettings): String = HybrisHacHttpClient.getInstance(project)
        .login(project, testSettings)

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
            .widthGroup("topComboBoxes")
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
                updateEnvironmentsComboBox()
                updatePortTextField()
                updateCredentials()
            }.component
            actionButton(ReloadEnvironmentsAction(subscriptionComboBox, this@RemoteHacConnectionDialog)).enabledIf(
                ComboBoxPredicate(subscriptionComboBox, { it != null })
            )
        }.layout(RowLayout.PARENT_GRID)

        if (settings.subscription?.isNotEmpty() ?: false && settings.environment?.isNotEmpty() ?: false && environmentsComboBoxModel.isEmpty) {
            environmentsComboBoxModel.update(listOf(settings.environment))
        }

        row {
            label("Environment:")
                .comment("Optional: Select an environment to use for this connection.")
            environmentComboBox = comboBox(
                environmentsComboBoxModel,
                renderer = SimpleListCellRenderer.create { label, value, _ ->
                    if (subscriptionComboBox.selectedItem != null && value != null) {
                        label.icon = HybrisIcons.Module.CCV2
                        label.text = "${subscriptionComboBox.selectedItem}.${value}"
                    } else {
                        label.text = ""
                    }
                }
            )
            .widthGroup("topComboBoxes")
            .bindItem(settings::environment.toNullableProperty(defaultValue = ""))
            .enabledIf(ComboBoxPredicate(subscriptionComboBox, { it != null }))
            .onChanged {
                updateHostsComboBox()
                updateReplicaIdsComboBox()
                updateCredentials()
            }
            .component
        }.layout(RowLayout.PARENT_GRID)

        row {
            label("Service:")
                .comment("Optional: Select a service to use for this connection.")
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
                updateHostsComboBox()
                updateReplicaIdsComboBox()
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
                hostEditableComboBox = comboBox(
                    hostsComboBoxModel,
                    renderer = SimpleListCellRenderer.create("") { it }
                )
                .comment("Host name or IP address")
                .align(AlignX.FILL)
                .bindItem(settings::hostIP.toNonNullableProperty(HybrisConstants.DEFAULT_HOST_URL))
                .onChanged { urlPreviewLabel.text = generateUrl() }
                .addValidationRule("Address cannot be blank.") { it.selectedItem?.toString().isNullOrBlank() }
                .component.apply {
                    isEditable = true
                }
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
                label("Session Cookie Name:")
                sessionCookieNameTextField = textField()
                    .comment("Optional: override the session cookie name. Default is JSESSIONID.")
                    .align(AlignX.FILL)
                    .bindText(settings::sessionCookieName.toNonNullableProperty(HybrisConstants.DEFAULT_SESSION_COOKIE_NAME))
                    .apply { component.text = "" }
                    .component
            }.layout(RowLayout.PARENT_GRID)

            row {
                label("Replica Id:")
                replicaIdComboBox = comboBox(
                    replicaIdsComboBoxModel,
                    renderer = SimpleListCellRenderer.create("") { it }
                )
                .comment("Optional: Target a specific replica.")
                .align(AlignX.FILL)
                .bindItem(
                    getter = { ->
                        settings.replicaId ?: ""
                    },
                    setter = { selected -> settings.replicaId = (selected?.toString() ?: "") }
                )
                .component.apply {
                    isEditable = true
                    if (settings.replicaId?.isNotBlank() == true) {
                        selectedItem = settings.replicaId
                    }
                }
            }.layout(RowLayout.PARENT_GRID)

            if (isWindows()) {
                wslHostConfiguration()
            }
        }

        group("Credentials") {
            row {
                label("Username:")
                usernameTextField = textField()
                    //.widthGroup("credentials")
                    .enabled(false)
                    .addValidationRule("Username cannot be blank.") { it.text.isNullOrBlank() }
                    .component
            }.layout(RowLayout.PARENT_GRID)

            row {
                label("Password:")
                passwordTextField = passwordField()
                    //.widthGroup("credentials")
                    .align(AlignX.FILL)
                    .enabled(false)
                    .addValidationRule("Password cannot be blank.") { it.password.isEmpty() }
                    .component
                actionButton(ShowPasswordAction(passwordTextField))
            }.layout(RowLayout.PARENT_GRID)
        }

        subscriptionComboBox.addItemListener { e ->
            e.stateChange
            if (subscriptionComboBox.selectedItem == null) {
                environmentComboBox.selectedItem = ""
                environmentComboBox.isEnabled = false
            }
        }
        hostEditableComboBox.selectedItem = settings.hostIP ?: HybrisConstants.DEFAULT_HOST_URL

    }

    fun diableOtherComboBoxes(comboBox: JComboBox<*>) {
        // subscriptionComboBox.isEnabled = (subscriptionComboBox == comboBox)
        // environmentComboBox.isEnabled = (environmentComboBox == comboBox)
        // serviceComboBox.isEnabled = (serviceComboBox == comboBox)
        // hostEditableComboBox.isEditable = (hostEditableComboBox == comboBox)
        // replicaIdComboBox.isEditable = (replicaIdComboBox == comboBox)
    }

    fun enableAll(comboBox: JComboBox<*>) {
        // subscriptionComboBox.isEnabled = true
        // environmentComboBox.isEnabled = true
        // serviceComboBox.isEnabled = true
        // hostEditableComboBox.isEditable = true
        // replicaIdComboBox.isEditable = true
    }

    fun updateEnvironmentsComboBox() {
        if (subscriptionComboBox.selectedItem != null) {
            CCv2Service.getInstance(project).fetchAvailableEnvironments(
                subscriptionComboBox.selectedItem as CCv2Subscription,
                {
                    diableOtherComboBoxes(subscriptionComboBox)
                },
                { environments ->
                    environmentsComboBoxModel.update(with(Regex("""([dsp])(\d+)""")) {
                        environments.map { it.code }
                            .sortedWith(compareBy(
                                { code -> if (matchEntire(code) != null) 0 else 1 },
                                { code -> matchEntire(code)?.groupValues?.get(1)?.let { when (it) { "d" -> 0; "s" -> 1; "p" -> 2; else -> 3 } } ?: 3 },
                                { code -> matchEntire(code)?.groupValues?.get(2)?.toIntOrNull() ?: Int.MAX_VALUE },
                                { code -> code }
                            ))
                    }
                    )
                    if (environments.isNotEmpty()) environmentComboBox.selectedIndex = 0
                    enableAll(subscriptionComboBox)
                }
            )
        } else {
            settings.environment = ""
            environmentComboBox.selectedItem = ""
        }
    }

    fun updateHostsComboBox() {
        subscriptionComboBox.selectedItem?.let{ sub ->
            if (environmentComboBox.selectedItem?.toString()?.isNotBlank() ?: false) {
                CCv2Service.getInstance(project).fetchEnvironment(
                    subscriptionComboBox.selectedItem as CCv2Subscription,
                    environmentComboBox.selectedItem?.toString()!!,
                    {
                        diableOtherComboBoxes(environmentComboBox)
                    },
                    { service ->
                        val endpoints = (
                            if (serviceComboBox.selectedItem?.toString()?.isNotBlank() == true)
                                service?.endpoints?.filter{ ep -> ep.k8sService == serviceComboBox.selectedItem?.toString() }
                            else
                                service?.endpoints?.filter{ ep -> commerceServices.contains(ep.k8sService.lowercase()) }
                            ) ?: emptyList()
                        val hostNames = endpoints.map { ep -> ep.domainName }.toMutableList()
                        service?.webProxies?.firstOrNull { proxy -> proxy.code == "public" }?.let{ webProxy ->
                            serviceComboBox.selectedItem?.toString()?.let { service ->
                                webProxy.defaultDnsEntry?.let { defaultDnsEntry ->
                                    val defaultHost = if (serviceComboBox.selectedItem?.toString()?.isNotEmpty() == true) defaultDnsEntry.replace(Regex("^\\*"), service) else defaultDnsEntry
                                    if (!hostNames.contains(defaultHost)) {
                                        hostNames.add(0,defaultDnsEntry)
                                    }
                                }
                            }
                        }
                        hostsComboBoxModel.update(hostNames)
                        enableAll(environmentComboBox)
                    }
                )
            }
        }
    }

    fun updateReplicaIdsComboBox() {
        subscriptionComboBox.selectedItem?.let {subscription ->
            environmentComboBox.selectedItem?.let {environmentCode ->
                CCv2Service.getInstance(project).fetchEnvironmentServices(
                    subscription as CCv2Subscription,
                    environmentCode.toString(),
                    {
                        diableOtherComboBoxes(serviceComboBox)
                    },
                    { services ->

                        val matchingServices = (
                            if (serviceComboBox.selectedItem?.toString()?.isNotBlank() == true)
                                services?.firstOrNull{s -> s.code.lowercase() == "hcs_platform_${serviceComboBox.selectedItem}" }?.replicas
                            else
                                services?.filter{ s -> s.code.startsWith("hcs_platform_")}?.flatMap{ it.replicas }
                            ) ?: emptyList()

                        val ids = matchingServices.map { r -> r.name }.toMutableList()

                        if (serviceComboBox.selectedItem?.toString()?.isNotBlank() == true) {
                            ids.add("${serviceComboBox.selectedItem}-*")
                        }

                        replicaIdsComboBoxModel.update(ids)
                        enableAll(serviceComboBox)

                    }
                )
            }
        }
    }

    fun updateCredentials() {
        var credentials: Credentials? = null
        subscriptionComboBox.selectedItem?.let { subscription ->
            val subscriptionName = (subscription as CCv2Subscription).name
            environmentComboBox.selectedItem?.let { environmentCode ->
                credentials = PasswordSafe.instance.get(CredentialAttributes("ccv2.hac.${subscriptionName}.${environmentCode}"))
            }
            credentials = credentials ?: PasswordSafe.instance.get(CredentialAttributes("ccv2.hac.${subscriptionName}"))
        }
        credentials?.let{
            usernameTextField.text = it.userName
            passwordTextField.text = it.password.toString()
        }
    }

    fun updatePortTextField() {
        subscriptionComboBox.selectedItem?.let {subscription ->
            portTextField.text = ""
        }
    }

    companion object {
        val commerceServices = listOf("accstorefront", "api", "backoffice", "backgroundprocessing")
    }

}