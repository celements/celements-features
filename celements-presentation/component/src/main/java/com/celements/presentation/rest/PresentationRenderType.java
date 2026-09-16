/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import static com.celements.navigation.presentation.PresentationContentRenderer.RENDERED_CONTENT_HINT;
import static com.celements.navigation.presentation.PresentationContentRenderer.RENDERED_EXTRACT_HINT;

enum PresentationRenderType {

    RENDERED_CONTENT(RENDERED_CONTENT_HINT), RENDERED_EXTRACT(RENDERED_EXTRACT_HINT);

    private final String componentHint;

    PresentationRenderType(String componentHint) {
        this.componentHint = componentHint;
    }

    String componentHint() {
        return componentHint;
    }

    static PresentationRenderType fromRequestValue(String value) {
        for (PresentationRenderType type : values()) {
            if (type.componentHint.equals(value)) {
                return type;
            }
        }
        throw PresentationException.badRequest("invalid_render_type",
                "Unsupported renderType [" + PresentationDiagnostic.safeValue(value) + "]", null);
    }

}
