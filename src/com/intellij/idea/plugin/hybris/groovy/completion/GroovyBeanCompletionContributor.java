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

package com.intellij.idea.plugin.hybris.groovy.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ProcessingContext;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.*;

import java.util.List;

public class GroovyBeanCompletionContributor extends CompletionContributor {

    public static class SpringBean {

        String name;
        String type;

        public SpringBean(final String name, final String type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

    }

    public GroovyBeanCompletionContributor() {
        extend(CompletionType.BASIC,
            PlatformPatterns.psiElement(PsiElement.class),
            new CompletionProvider<>() {
            @Override
            protected void addCompletions(
                @NotNull final CompletionParameters parameters,
                @NotNull final ProcessingContext context,
                @NotNull final CompletionResultSet result) {
                final var project = parameters.getPosition().getProject();
                final var position = parameters.getPosition();
                for (SpringBean bean : fetchBeans()) {

                    try {

                        final var file = (GroovyFile) position.getContainingFile();

                        // final var variable = new GrLightVariable(
                        //      PsiManager.getInstance(project),
                        //      bean.getName(),
                        //      // bean.getType(),
                        //      getPsiType(bean.getType(), project),
                        //      position
                        // );

                        final var scriptClass = file.getScriptClass();

                        if (scriptClass != null) {

                            final var fields = scriptClass.getAllFields();

                            for (var f : fields) {
                                System.out.println(f);
                            }

                            final var field = new GrLightField(scriptClass, bean.getName(), getPsiType(bean.getType(), project), position);

                            // final var bindingVariableVariable = new GrBindingVariable(file, bean.getName());
                            // bindingVariableVariable.setType(getPsiType(bean.getType(), project));

                            final var variable = new GrLightVariable(
                                 PsiManager.getInstance(project),
                                 bean.getName(),
                                 // bean.getType(),
                                 getPsiType(bean.getType(), project),
                                 position
                            );

                            final var scriptField = new GrScriptField(field, (GroovyScriptClass) scriptClass);

                            result.addElement(LookupElementBuilder.create(scriptField)
                                .withIcon(JetgroovyIcons.Groovy.Property)
                                .withTypeText(bean.type));

                        }

                    } catch (Exception e) {

                        System.out.println(e);

                    }

                }
            }
        });
    }

    private PsiClass getPsiClass(final String type, final Project project) {
        final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(type, GlobalSearchScope.allScope(project));
        return psiClass != null
            ? psiClass
            : PsiType.getJavaLangObject(PsiManager.getInstance(project), GlobalSearchScope.allScope(project)).resolve();
    }

    private PsiType getPsiType(final String type, final Project project) {
        final var psiClass = JavaPsiFacade.getInstance(project).findClass(type, GlobalSearchScope.allScope(project));
        return psiClass != null
            ? JavaPsiFacade.getElementFactory(project).createType(psiClass)
            : PsiType.getJavaLangObject(PsiManager.getInstance(project), GlobalSearchScope.allScope(project));
    }

    public List<SpringBean> fetchBeans() {
        return List.of(new SpringBean("i18NServiceAlias","de.hybris.platform.servicelayer.i18n.I18NService"));
    }

}
