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

package com.intellij.idea.plugin.hybris.spring;

import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;

public class CustomTreeToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        JPanel panel = new JPanel(new BorderLayout());

        // Create search field
        JTextField searchField = new JTextField();
        searchField.setBorder(JBUI.Borders.empty(5));

        // Create tree
        SimpleTree tree = new SimpleTree();
        CustomTreeStructure treeStructure = new CustomTreeStructure(project, "");

        // Instead of tree.getBuilder().updateFrom(treeStructure.getRootElement());
         DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) treeStructure.getRootElement();
         tree.setModel(new javax.swing.tree.DefaultTreeModel(rootNode));

         // Wrap tree in scroll pane
        JBScrollPane scrollPane = new JBScrollPane(tree);

        // Add components to panel
        panel.add(searchField, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Add search functionality
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateTree();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateTree();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateTree();
            }

            private void updateTree() {
                String query = searchField.getText().trim().toLowerCase();
                treeStructure.setSearchQuery(query);
                tree.setModel(new javax.swing.tree.DefaultTreeModel((DefaultMutableTreeNode) treeStructure.getRootElement()));
            }

        });

        // Add panel to tool window
        toolWindow.getComponent().getParent().add(panel);
    }

    private static class CustomTreeStructure extends SimpleTreeStructure {
        private final Project project;
        private String searchQuery;
        private final DefaultMutableTreeNode root;

        public CustomTreeStructure(Project project, String searchQuery) {
            this.project = project;
            this.searchQuery = searchQuery;
            this.root = new DefaultMutableTreeNode("Project");
            buildTree();
        }

        public void setSearchQuery(String query) {
            this.searchQuery = query.toLowerCase();
            buildTree();
        }

        private void buildTree() {
            root.removeAllChildren();
            VirtualFile[] rootFiles = project.getBaseDir().getChildren();
            for (VirtualFile file : rootFiles) {
                addNodeRecursively(file, root);
            }
        }

        private void addNodeRecursively(VirtualFile file, DefaultMutableTreeNode parent) {
            // Check if file matches search query
            boolean matches = searchQuery.isEmpty() || file.getName().toLowerCase().contains(searchQuery);

            // Always process directories to check their children
            if (file.isDirectory()) {
                DefaultMutableTreeNode dirNode = new DefaultMutableTreeNode(file.getName());
                for (VirtualFile child : file.getChildren()) {
                    addNodeRecursively(child, dirNode);
                }
                // Only add directory if it matches or has matching children
                if (matches || !dirNode.isLeaf()) {
                    parent.add(dirNode);
                }
            } else {
                // Add file if it matches and is a text file
                PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                if (matches && psiFile != null && file.getFileType() instanceof PlainTextFileType) {
                    parent.add(new DefaultMutableTreeNode(file.getName()));
                }
            }
        }

        @Override
        public Object getRootElement() {
            return root;
        }
    }
}
