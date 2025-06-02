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

package com.intellij.idea.plugin.hybris.groovy.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.MutableCollectionComboBoxModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class JGroovySelectWebContextAction extends ComboBoxAction implements DumbAware {

    private final MutableCollectionComboBoxModel<String> model = new MutableCollectionComboBoxModel<>();

    public JGroovySelectWebContextAction() {
        model.addElement("/authorizationserver/springmvc");
        model.addElement("/occ/springmvc-v2");
        model.addElement("/occ/springmvc");
        model.addElement("/backoffice");
        model.addElement("/hac/springmvc");
        model.addElement("/yacceleratorstorefront/springmvc");
    }

    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        DefaultActionGroup group = new DefaultActionGroup();
        for (int i = 0; i < model.getSize(); i++) {
            String item = model.getElementAt(i);
            group.add(new DumbAwareAction(item) {
                @Override
                public void actionPerformed(AnActionEvent e) {
                    // Handle item selection
                    System.out.println("Selected: " + item);
                }
            });
        }
        return group;
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        Presentation presentation = e.getPresentation();
        presentation.setText("Select an option"); // Optional: dynamic label
    }

    @NotNull
    @Override
    public JComponent createCustomComponent(@NotNull final Presentation presentation, @NotNull final String place) {
        final JComponent comboBox = super.createCustomComponent(presentation, place);
        comboBox.setBorder(BorderFactory.createEmptyBorder());
        return comboBox;
    }

}
