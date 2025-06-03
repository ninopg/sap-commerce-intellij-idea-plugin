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
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.idea.plugin.hybris.common.utils.HybrisIcons;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;

public class HacBeansCompletionContributor extends CompletionContributor {

    public HacBeansCompletionContributor() {

        // Restrict to Groovy files
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().withLanguage(GroovyLanguage.INSTANCE),
            new CompletionProvider<CompletionParameters>() {

                @Override
                protected void addCompletions(@NotNull final CompletionParameters parameters, @NotNull final ProcessingContext context, @NotNull final CompletionResultSet result) {
                    // Create a custom result set to decorate existing suggestions
                    CompletionResultSet customResult = result.withRelevanceSorter(CompletionSorter.defaultSorter(parameters, result.getPrefixMatcher()));
                    customResult.runRemainingContributors(parameters, completionResult -> {
                        LookupElement element = completionResult.getLookupElement();
                        // Check if the suggestion is a dynamic property (example heuristic)
                        if (isDynamicProperty(element)) {
                            // Decorate the existing LookupElement
                            customResult.addElement(new LookupElementDecorator<LookupElement>(element) {
                                @Override
                                public void renderElement(LookupElementPresentation presentation) {
                                    super.renderElement(presentation);
                                    // Customize icon
                                    presentation.setIcon(HybrisIcons.Y.INSTANCE.getLOGO_BLUE());
                                    // Add extra text
                                    presentation.setTailText(" (Dynamic Property)", true);
                                    presentation.setTypeText("Dynamic");
                                }
                            });
                        } else {
                            // Pass through unchanged
                            customResult.addElement(element);
                        }
                    });
                }

                // Heuristic to identify dynamic properties (customize based on your needs)
                private boolean isDynamicProperty(LookupElement element) {
                    // Example: Check if the element is a dynamic property
                    // You may need to inspect the PSI or element's properties
                    String lookupString = element.getLookupString();
                    // Replace with actual logic to identify dynamic properties
                    return lookupString.endsWith("Converter") || lookupString.endsWith("Controller") || lookupString.endsWith("Service");
                }
            });

    }

    @Override
    public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull final CompletionResultSet result) {
        super.fillCompletionVariants(parameters, result);

        // Assuming we're adding a dynamic property suggestion
        LookupElement element = LookupElementBuilder.create("dynamicProperty")
            .withIcon(HybrisIcons.Y.INSTANCE.getLOGO_BLUE()) // Replace with actual icon
            .withTypeText("Dynamic Property");

        result.addElement(new LookupElementDecorator<LookupElement>(element) {
            @Override
            public void renderElement(LookupElementPresentation presentation) {
                super.renderElement(presentation);
                presentation.setIcon(HybrisIcons.Y.INSTANCE.getLOGO_GREEN());
                presentation.setTailText(" (Custom Dynamic Property)", true);
            }
        });

    }

}
