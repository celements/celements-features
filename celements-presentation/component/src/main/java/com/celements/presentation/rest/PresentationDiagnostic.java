/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import java.util.Collection;
import java.util.stream.Collectors;

import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;

final class PresentationDiagnostic {

    private static final int MAX_VALUE_LENGTH = 160;

    private PresentationDiagnostic() {
    }

    static String safeValue(Object value) {
        String safeValue = String.valueOf(value).replace('\r', ' ').replace('\n', ' ');
        return safeValue.length() <= MAX_VALUE_LENGTH ? safeValue : safeValue.substring(0, MAX_VALUE_LENGTH) + "...";
    }

    static String safeValues(Collection<?> values) {
        return values.stream().map(PresentationDiagnostic::safeValue).collect(Collectors.joining(", ", "[", "]"));
    }

    static String safeReference(DocumentReference reference) {
        if (reference == null) {
            return "unresolved";
        }
        String spaces = reference.getSpaceReferences().stream().map(SpaceReference::getName)
                .collect(Collectors.joining("."));
        return safeValue(spaces + "." + reference.getName());
    }

    static String safeReferences(Collection<DocumentReference> references) {
        return references.stream().map(PresentationDiagnostic::safeReference)
                .collect(Collectors.joining(", ", "[", "]"));
    }

}
