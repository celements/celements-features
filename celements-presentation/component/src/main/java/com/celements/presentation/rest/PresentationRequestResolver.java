/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import java.util.HashSet;
import java.util.List;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xwiki.model.reference.DocumentReference;

import com.celements.model.context.ModelContext;
import com.celements.model.util.ModelUtils;

@Component
public class PresentationRequestResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(PresentationRequestResolver.class);

    private final ModelUtils modelUtils;
    private final ModelContext context;

    @Inject
    public PresentationRequestResolver(ModelUtils modelUtils, ModelContext context) {
        this.modelUtils = modelUtils;
        this.context = context;
    }

    public DocumentReference resolvePresentationReference(List<String> values) {
        if (values.size() != 1) {
            throw PresentationException.badRequest("invalid_presentation_reference",
                    "Expected one presentationConfigFullName but received " + values.size() + " "
                            + PresentationDiagnostic.safeValues(values),
                    null);
        }
        return resolveCanonicalReference(values.get(0), "presentationConfigFullName", "invalid_presentation_reference");
    }

    public List<DocumentReference> resolveSlideReferences(List<String> values) {
        List<DocumentReference> refs = values.stream()
                .map(value -> resolveCanonicalReference(value, "slideFullName", "invalid_slide_selection")).toList();
        if (refs.isEmpty() || (refs.size() != new HashSet<>(refs).size())) {
            throw PresentationException.badRequest("invalid_slide_selection",
                    "Empty or duplicate slideFullName selection " + PresentationDiagnostic.safeValues(values), null);
        }
        return refs;
    }

    public PresentationRenderType resolveRenderType(List<String> renderTypes) {
        if (renderTypes.size() != 1) {
            throw PresentationException.badRequest("invalid_render_type", "Expected one renderType but received "
                    + renderTypes.size() + " " + PresentationDiagnostic.safeValues(renderTypes), null);
        }
        return PresentationRenderType.fromRequestValue(renderTypes.get(0));
    }

    public void validateNavigationNumber(int navigationNumber) {
        if (navigationNumber < 1) {
            throw PresentationException.badRequest("invalid_navigation_number",
                    "navigationNumber must be positive but was " + navigationNumber, null);
        }
    }

    private DocumentReference resolveCanonicalReference(String value, String inputName, String errorCode) {
        try {
            if ((value == null) || value.isBlank()) {
                throw new IllegalArgumentException("blank reference");
            }
            DocumentReference ref = modelUtils.resolveRef(value, DocumentReference.class, context.getWikiRef());
            if (!ref.getWikiReference().equals(context.getWikiRef())
                    || !value.equals(modelUtils.serializeRefLocal(ref))) {
                throw new IllegalArgumentException("non-canonical reference");
            }
            return ref;
        } catch (IllegalArgumentException exc) {
            LOGGER.debug("Rejected presentation API document reference.", exc);
            throw PresentationException.badRequest(errorCode,
                    "Rejected " + inputName + " [" + PresentationDiagnostic.safeValue(value) + "]", exc);
        }
    }

}
