/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;

import com.celements.model.context.ModelContext;
import com.celements.model.util.ModelUtils;

public class PresentationRequestResolverTest {

    private ModelUtils modelUtils;
    private ModelContext context;
    private PresentationRequestResolver resolver;
    private WikiReference wikiRef;

    @Before
    public void setUp() {
        modelUtils = createMock(ModelUtils.class);
        context = createMock(ModelContext.class);
        resolver = new PresentationRequestResolver(modelUtils, context);
        wikiRef = new WikiReference("xwiki");
    }

    @Test
    public void resolvePresentationReference_acceptsCanonicalLocalReference() {
        DocumentReference docRef = new DocumentReference("xwiki", "Content", "WebHome");
        expect(context.getWikiRef()).andReturn(wikiRef).times(2);
        expect(modelUtils.resolveRef("Content.WebHome", DocumentReference.class, wikiRef)).andReturn(docRef);
        expect(modelUtils.serializeRefLocal(docRef)).andReturn("Content.WebHome");
        replay(modelUtils, context);
        assertSame(docRef, resolver.resolvePresentationReference(List.of("Content.WebHome")));
        verify(modelUtils, context);
    }

    @Test
    public void resolvePresentationReference_rejectsRepeatedReference() {
        assertError("invalid_presentation_reference",
                () -> resolver.resolvePresentationReference(List.of("Content.WebHome", "Content.Other")));
    }

    @Test
    public void resolvePresentationReference_rejectsBlankAndMalformedReference() {
        expect(context.getWikiRef()).andReturn(wikiRef);
        expect(modelUtils.resolveRef("malformed[", DocumentReference.class, wikiRef))
                .andThrow(new IllegalArgumentException("malformed"));
        replay(modelUtils, context);
        assertError("invalid_presentation_reference", () -> resolver.resolvePresentationReference(List.of("")));
        PresentationException malformed = assertError("invalid_presentation_reference",
                () -> resolver.resolvePresentationReference(List.of("malformed[")));
        assertEquals("Rejected presentationConfigFullName [malformed[]", malformed.getDiagnosticContext());
        assertEquals("malformed", malformed.getCause().getMessage());
        verify(modelUtils, context);
    }

    @Test
    public void resolvePresentationReference_rejectsNonCanonicalAndCrossWikiReference() {
        DocumentReference localRef = new DocumentReference("xwiki", "Content", "WebHome");
        DocumentReference crossWikiRef = new DocumentReference("other", "Content", "WebHome");
        expect(context.getWikiRef()).andReturn(wikiRef).times(4);
        expect(modelUtils.resolveRef("Content.WebHome.", DocumentReference.class, wikiRef)).andReturn(localRef);
        expect(modelUtils.serializeRefLocal(localRef)).andReturn("Content.WebHome");
        expect(modelUtils.resolveRef("other:Content.WebHome", DocumentReference.class, wikiRef))
                .andReturn(crossWikiRef);
        replay(modelUtils, context);
        assertError("invalid_presentation_reference",
                () -> resolver.resolvePresentationReference(List.of("Content.WebHome.")));
        assertError("invalid_presentation_reference",
                () -> resolver.resolvePresentationReference(List.of("other:Content.WebHome")));
        verify(modelUtils, context);
    }

    @Test
    public void resolvePresentationReference_rejectsExplicitCurrentWiki() {
        DocumentReference docRef = new DocumentReference("xwiki", "Content", "WebHome");
        expect(context.getWikiRef()).andReturn(wikiRef).times(2);
        expect(modelUtils.resolveRef("xwiki:Content.WebHome", DocumentReference.class, wikiRef)).andReturn(docRef);
        expect(modelUtils.serializeRefLocal(docRef)).andReturn("Content.WebHome");
        replay(modelUtils, context);
        assertError("invalid_presentation_reference",
                () -> resolver.resolvePresentationReference(List.of("xwiki:Content.WebHome")));
        verify(modelUtils, context);
    }

    @Test
    public void resolveSlideReferences_rejectsDuplicates() {
        DocumentReference docRef = new DocumentReference("xwiki", "Content", "Slide");
        expect(context.getWikiRef()).andReturn(wikiRef).times(4);
        expect(modelUtils.resolveRef("Content.Slide", DocumentReference.class, wikiRef)).andReturn(docRef).times(2);
        expect(modelUtils.serializeRefLocal(docRef)).andReturn("Content.Slide").times(2);
        replay(modelUtils, context);
        assertError("invalid_slide_selection",
                () -> resolver.resolveSlideReferences(List.of("Content.Slide", "Content.Slide")));
        verify(modelUtils, context);
    }

    @Test
    public void resolveSlideReferences_rejectsEmptyAndBlankSelection() {
        replay(modelUtils, context);
        assertError("invalid_slide_selection", () -> resolver.resolveSlideReferences(List.of()));
        assertError("invalid_slide_selection", () -> resolver.resolveSlideReferences(List.of("")));
        verify(modelUtils, context);
    }

    @Test
    public void resolveRenderType_isExactAndSingular() {
        assertSame(PresentationRenderType.RENDERED_CONTENT, resolver.resolveRenderType(List.of("renderedContent")));
        assertSame(PresentationRenderType.RENDERED_EXTRACT, resolver.resolveRenderType(List.of("renderedExtract")));
        PresentationException invalidValue = assertError("invalid_render_type",
                () -> resolver.resolveRenderType(List.of("RenderedContent")));
        assertEquals("Unsupported renderType [RenderedContent]", invalidValue.getDiagnosticContext());
        assertError("invalid_render_type",
                () -> resolver.resolveRenderType(List.of("renderedContent", "renderedExtract")));
    }

    @Test
    public void validateNavigationNumber_requiresPositiveValue() {
        resolver.validateNavigationNumber(1);
        resolver.validateNavigationNumber(Integer.MAX_VALUE);
        PresentationException invalid = assertError("invalid_navigation_number",
                () -> resolver.validateNavigationNumber(0));
        assertEquals("navigationNumber must be positive but was 0", invalid.getDiagnosticContext());
    }

    private PresentationException assertError(String expectedCode, Runnable action) {
        try {
            action.run();
            fail("Expected PresentationException");
        } catch (PresentationException exc) {
            assertEquals(expectedCode, exc.getCode());
            return exc;
        }
        throw new AssertionError("unreachable");
    }

}
