/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.same;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.WikiReference;

import com.celements.model.context.ModelContext;
import com.celements.model.util.ModelUtils;
import com.celements.navigation.INavigation;
import com.celements.navigation.TreeNode;
import com.xpn.xwiki.XWikiContext;

public class PresentationApiServiceTest {

    private ModelUtils modelUtils;
    private ModelContext context;
    private PresentationBatchRenderer batchRenderer;
    private PresentationRequestResolver requestResolver;
    private PresentationConfigReader configReader;
    private PresentationSlideListService slideListService;
    private PresentationMetadataMapper metadataMapper;
    private PresentationApiService service;
    private DocumentReference configRef;
    private DocumentReference firstRef;
    private DocumentReference secondRef;
    private PresentationDefinition definition;
    private List<TreeNode> slides;

    @Before
    public void prepareTest() {
        modelUtils = createMock(ModelUtils.class);
        context = createMock(ModelContext.class);
        batchRenderer = createMock(PresentationBatchRenderer.class);
        requestResolver = createMock(PresentationRequestResolver.class);
        configReader = createMock(PresentationConfigReader.class);
        slideListService = createMock(PresentationSlideListService.class);
        metadataMapper = createMock(PresentationMetadataMapper.class);
        service = new PresentationApiService(modelUtils, context, batchRenderer, requestResolver, configReader,
                slideListService, metadataMapper);
        configRef = new DocumentReference("xwiki", "Content", "WebHome");
        firstRef = new DocumentReference("xwiki", "Content", "First");
        secondRef = new DocumentReference("xwiki", "Content", "Second");
        definition = new PresentationDefinition(configRef, new SpaceReference("Content", new WikiReference("xwiki")),
                "homepage", "layoutEditor", "cel_cm_presentation_treenode");
        slides = List.of(new TreeNode(firstRef, null, 1), new TreeNode(secondRef, null, 2));
    }

    @Test
    public void test_getPresentation_validEmptyPresentationReturnsEnvelope() {
        requestResolver.validateNavigationNumber(1);
        expect(requestResolver.resolvePresentationReference(List.of("Content.WebHome"))).andReturn(configRef);
        expect(configReader.read(configRef)).andReturn(definition);
        expect(slideListService.getVisibleSlides(definition)).andReturn(List.of());
        INavigation navigation = createMock(INavigation.class);
        expect(metadataMapper.createNavigation(definition, 1)).andReturn(navigation);
        expect(modelUtils.serializeRefLocal(configRef)).andReturn("Content.WebHome");
        expect(metadataMapper.map(definition, List.of(), navigation)).andReturn(List.of());
        XWikiContext xwikiContext = new XWikiContext();
        xwikiContext.setLanguage("de");
        expect(context.getXWikiContext()).andReturn(xwikiContext);
        replayAll();
        PresentationResponse result = service.getPresentation(List.of("Content.WebHome"), 1);
        assertEquals("Content.WebHome", result.presentationConfigFullName());
        assertEquals("layoutEditor", result.presentationType());
        assertEquals("de", result.language());
        assertEquals(List.of(), result.slides());
        verifyAll();
    }

    @Test
    public void test_renderSlides_validatesCompleteSelectionThenPreservesRequestedOrder() {
        requestResolver.validateNavigationNumber(1);
        List<DocumentReference> requestOrder = List.of(secondRef, firstRef);
        List<RenderedSlideResponse> rendered = List.of(
                new RenderedSlideResponse("Content.Second", "N1:Content:Content.Second", "second"),
                new RenderedSlideResponse("Content.First", "N1:Content:Content.First", "first"));
        expect(requestResolver.resolvePresentationReference(List.of("Content.WebHome"))).andReturn(configRef);
        expect(requestResolver.resolveSlideReferences(List.of("Content.Second", "Content.First")))
                .andReturn(requestOrder);
        expect(requestResolver.resolveRenderType(List.of("renderedContent")))
                .andReturn(PresentationRenderType.RENDERED_CONTENT);
        expect(configReader.read(configRef)).andReturn(definition);
        expect(slideListService.getVisibleSlides(definition)).andReturn(slides);
        INavigation navigation = createMock(INavigation.class);
        expect(metadataMapper.createNavigation(definition, 1)).andReturn(navigation);
        slideListService.validateSelection(definition, slides, requestOrder);
        expect(batchRenderer.render(same(definition), same(requestOrder), same(navigation),
                same(PresentationRenderType.RENDERED_CONTENT))).andReturn(rendered);
        XWikiContext xwikiContext = new XWikiContext();
        xwikiContext.setLanguage("de");
        expect(context.getXWikiContext()).andReturn(xwikiContext);
        replayAll();
        RenderedPresentationResponse result = service.renderSlides(List.of("Content.WebHome"),
                List.of("Content.Second", "Content.First"), List.of("renderedContent"), 1);
        assertEquals("de", result.language());
        assertEquals(rendered, result.slides());
        verifyAll();
    }

    @Test
    public void test_renderSlides_invalidMemberStopsBeforeRenderingAtomically() {
        requestResolver.validateNavigationNumber(7);
        List<DocumentReference> requested = List.of(firstRef, secondRef);
        expect(requestResolver.resolvePresentationReference(List.of("Content.WebHome"))).andReturn(configRef);
        expect(requestResolver.resolveSlideReferences(List.of("Content.First", "Content.Second"))).andReturn(requested);
        expect(requestResolver.resolveRenderType(List.of("renderedExtract")))
                .andReturn(PresentationRenderType.RENDERED_EXTRACT);
        expect(configReader.read(configRef)).andReturn(definition);
        expect(slideListService.getVisibleSlides(definition)).andReturn(slides);
        expect(metadataMapper.createNavigation(definition, 7)).andReturn(createMock(INavigation.class));
        slideListService.validateSelection(definition, slides, requested);
        expectLastCall().andThrow(PresentationException.notFound("slide_not_found", "member unavailable"));
        replayAll();
        try {
            service.renderSlides(List.of("Content.WebHome"), List.of("Content.First", "Content.Second"),
                    List.of("renderedExtract"), 7);
            fail("Expected slide_not_found");
        } catch (PresentationException exc) {
            assertEquals("slide_not_found", exc.getCode());
        }
        verifyAll();
    }

    private void replayAll() {
        replay(modelUtils, context, batchRenderer, requestResolver, configReader, slideListService, metadataMapper);
    }

    private void verifyAll() {
        verify(modelUtils, context, batchRenderer, requestResolver, configReader, slideListService, metadataMapper);
    }

}
