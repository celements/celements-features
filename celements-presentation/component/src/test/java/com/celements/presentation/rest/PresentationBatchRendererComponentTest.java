/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import static com.celements.execution.XWikiExecutionProp.XWIKI_CONTEXT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.velocity.VelocityContext;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.model.reference.DocumentReference;

import com.celements.common.classes.IClassCollectionRole;
import com.celements.common.test.AbstractComponentTest;
import com.celements.model.util.ModelUtils;
import com.celements.navigation.INavigation;
import com.celements.navigation.presentation.PresentationContentRenderer;
import com.celements.navigation.presentation.RenderedContentPresentationType;
import com.celements.navigation.presentation.RenderedExtractPresentationType;
import com.celements.rendering.RenderCommand;
import com.celements.web.classcollections.DocumentDetailsClasses;
import com.celements.web.service.IWebUtilsService;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

public class PresentationBatchRendererComponentTest extends AbstractComponentTest {

    private ComponentManager componentManager;
    private ModelUtils modelUtils;
    private INavigation navigation;
    private Execution execution;
    private PresentationBatchRenderer batchRenderer;
    private DocumentReference firstRef;
    private DocumentReference secondRef;
    private PresentationDefinition definition;
    private XWikiContext originalContext;
    private VelocityContext originalVelocityContext;

    @Before
    public void prepareTest() throws Exception {
        componentManager = getBeanFactory().getBean(ComponentManager.class);
        modelUtils = createDefaultMock(ModelUtils.class);
        navigation = createDefaultMock(INavigation.class);
        execution = getBeanFactory().getBean(Execution.class);
        batchRenderer = new PresentationBatchRenderer(componentManager, modelUtils, execution);
        firstRef = new DocumentReference(DEFAULT_DB, "Content", "First");
        secondRef = new DocumentReference(DEFAULT_DB, "Content", "Second");
        definition = new PresentationDefinition(new DocumentReference(DEFAULT_DB, "Config", "WebHome"),
                firstRef.getLastSpaceReference(), "", "default", "presentation");
        originalContext = getXContext();
        originalVelocityContext = new VelocityContext();
        originalContext.put("vcontext", originalVelocityContext);
        execution.getContext().setProperty("velocityContext", originalVelocityContext);
    }

    @Test
    public void test_renderedContent_registeredRendererPreservesRichFixtureAndIsolation() throws Exception {
        String firstHtml = fixture("rendered-content-first.html");
        String secondHtml = fixture("rendered-content-second.html");
        RenderCommand renderCommand = createMock(RenderCommand.class);
        PresentationContentRenderer renderer = getBeanFactory().getBean("renderedContent",
                PresentationContentRenderer.class);
        assertTrue(renderer instanceof RenderedContentPresentationType);
        ReflectionTestUtils.setField(renderer, "renderCmd", renderCommand);
        expectCommonResponseMetadata();
        AtomicInteger invocation = new AtomicInteger();
        expect(renderCommand.renderCelementsDocument(eq(firstRef), eq("view")))
                .andAnswer(() -> renderFixtureAndMutate(invocation, firstHtml));
        expect(renderCommand.renderCelementsDocument(eq(secondRef), eq("view")))
                .andAnswer(() -> renderFixtureAndMutate(invocation, secondHtml));
        replayDefault(renderCommand);
        List<RenderedSlideResponse> result = batchRenderer.render(definition, List.of(firstRef, secondRef), navigation,
                PresentationRenderType.RENDERED_CONTENT);
        verifyDefault(renderCommand);
        assertRenderedBatch(result, firstHtml, secondHtml);
        assertTrue(result.get(0).renderedContent().contains("href=\"/xwiki/bin/view/Content/Second\""));
        assertTrue(result.get(0).renderedContent().contains("/xwiki/bin/download/Content/First/guide.pdf"));
        assertTrue(result.get(0).renderedContent().contains("<section"));
        assertTrue(result.get(0).renderedContent().contains("data-editorial-crop=\"wide\""));
        assertTrue(result.get(1).renderedContent().contains("<video"));
        assertOriginalContextsRestored();
    }

    @Test
    public void test_renderedExtract_registeredRendererUsesFixturesInOrderAndIsolation() throws Exception {
        String firstHtml = fixture("rendered-extract-first.html");
        String secondHtml = fixture("rendered-extract-second.html");
        RenderCommand renderCommand = createMock(RenderCommand.class);
        IWebUtilsService webUtilsService = createMock(IWebUtilsService.class);
        PresentationContentRenderer renderer = getBeanFactory().getBean("renderedExtract",
                PresentationContentRenderer.class);
        assertTrue(renderer instanceof RenderedExtractPresentationType);
        ReflectionTestUtils.setField(renderer, "renderCmd", renderCommand);
        ReflectionTestUtils.setField(renderer, "webUtilsService", webUtilsService);
        XWikiDocument firstDoc = extractDocument(firstRef, firstHtml);
        XWikiDocument secondDoc = extractDocument(secondRef, secondHtml);
        expect(webUtilsService.getInheritedTemplatedPath(anyObject(DocumentReference.class)))
                .andReturn(":celTemplates/RenderedExtract.vm").times(2);
        expect(getMock(XWiki.class).getDocument(eq(firstRef), anyObject(XWikiContext.class))).andReturn(firstDoc)
                .times(2);
        expect(getMock(XWiki.class).getDocument(eq(secondRef), anyObject(XWikiContext.class))).andReturn(secondDoc)
                .times(2);
        expectCommonResponseMetadata();
        AtomicInteger invocation = new AtomicInteger();
        expect(renderCommand.renderTemplatePath(eq(":celTemplates/RenderedExtract.vm"), eq(DEFAULT_LANG), eq("")))
                .andAnswer(() -> renderCurrentExtractAndMutate(invocation));
        expect(renderCommand.renderTemplatePath(eq(":celTemplates/RenderedExtract.vm"), eq(DEFAULT_LANG), eq("")))
                .andAnswer(() -> renderCurrentExtractAndMutate(invocation));
        replayDefault(renderCommand, webUtilsService);
        List<RenderedSlideResponse> result = batchRenderer.render(definition, List.of(firstRef, secondRef), navigation,
                PresentationRenderType.RENDERED_EXTRACT);
        verifyDefault(renderCommand, webUtilsService);
        assertRenderedBatch(result, firstHtml, secondHtml);
        assertTrue(result.get(0).renderedContent().contains("<strong>semantic emphasis</strong>"));
        assertTrue(result.get(0).renderedContent().contains("data-editorial-focus=\"center\""));
        assertTrue(result.get(1).renderedContent().contains("/xwiki/bin/download/Content/Second/factsheet.pdf"));
        assertTrue(result.get(1).renderedContent().contains("<aside"));
        assertOriginalContextsRestored();
    }

    @Test
    public void test_registeredRendererFailureIsAtomicAndRestoresContexts() throws Exception {
        String firstHtml = fixture("rendered-content-first.html");
        RenderCommand renderCommand = createMock(RenderCommand.class);
        PresentationContentRenderer renderer = getBeanFactory().getBean("renderedContent",
                PresentationContentRenderer.class);
        ReflectionTestUtils.setField(renderer, "renderCmd", renderCommand);
        expectCommonResponseMetadata();
        expect(renderCommand.renderCelementsDocument(eq(firstRef), eq("view"))).andReturn(firstHtml);
        XWikiException failure = new XWikiException();
        expect(renderCommand.renderCelementsDocument(eq(secondRef), eq("view"))).andThrow(failure);
        replayDefault(renderCommand);
        try {
            batchRenderer.render(definition, List.of(firstRef, secondRef), navigation,
                    PresentationRenderType.RENDERED_CONTENT);
            fail("Expected rendering_failed");
        } catch (PresentationException exc) {
            assertEquals("rendering_failed", exc.getCode());
            assertSame(failure, exc.getCause());
        }
        verifyDefault(renderCommand);
        assertOriginalContextsRestored();
    }

    private void expectCommonResponseMetadata() {
        expect(modelUtils.serializeRefLocal(firstRef)).andReturn("Content.First");
        expect(navigation.getUniqueId(firstRef)).andReturn("N1:Content:Content.First");
        expect(modelUtils.serializeRefLocal(secondRef)).andReturn("Content.Second");
        expect(navigation.getUniqueId(secondRef)).andReturn("N1:Content:Content.Second");
    }

    private XWikiDocument extractDocument(DocumentReference docRef, String content) throws Exception {
        DocumentDetailsClasses classes = (DocumentDetailsClasses) getBeanFactory().getBean("celements.documentDetails",
                IClassCollectionRole.class);
        XWikiDocument doc = new XWikiDocument(docRef);
        BaseObject extract = new BaseObject();
        extract.setXClassReference(classes.getDocumentExtractClassRef(DEFAULT_DB));
        extract.setStringValue(DocumentDetailsClasses.FIELD_DOC_EXTRACT_LANGUAGE, DEFAULT_LANG);
        extract.setStringValue(DocumentDetailsClasses.FIELD_DOC_EXTRACT_CONTENT, content);
        doc.addXObject(extract);
        return doc;
    }

    private String renderFixtureAndMutate(AtomicInteger invocation, String fixture) {
        assertIsolatedAndMutate(invocation);
        return fixture;
    }

    private String renderCurrentExtractAndMutate(AtomicInteger invocation) {
        XWikiContext isolated = execution.getContext().get(XWIKI_CONTEXT).orElseThrow();
        String content = (String) ((VelocityContext) isolated.get("vcontext")).get("extractContent");
        assertIsolatedAndMutate(invocation);
        return content;
    }

    private void assertIsolatedAndMutate(AtomicInteger invocation) {
        ExecutionContext executionContext = execution.getContext();
        XWikiContext isolatedContext = executionContext.get(XWIKI_CONTEXT).orElseThrow();
        VelocityContext isolatedVelocity = (VelocityContext) executionContext.getProperty("velocityContext");
        assertNotSame(originalContext, isolatedContext);
        assertNotSame(originalVelocityContext, isolatedVelocity);
        assertFalse(isolatedContext.containsKey("slideLeak"));
        assertFalse(isolatedVelocity.containsKey("slideLeak"));
        int slideNumber = invocation.incrementAndGet();
        isolatedContext.put("slideLeak", slideNumber);
        isolatedVelocity.put("slideLeak", slideNumber);
    }

    private void assertRenderedBatch(List<RenderedSlideResponse> result, String firstHtml, String secondHtml) {
        assertEquals(List.of(new RenderedSlideResponse("Content.First", "N1:Content:Content.First", firstHtml),
                new RenderedSlideResponse("Content.Second", "N1:Content:Content.Second", secondHtml)), result);
        assertFalse(result.get(0).renderedContent().startsWith("<div "));
        assertFalse(result.get(1).renderedContent().startsWith("<div "));
        assertFalse(result.get(0).renderedContent().contains("cel_cm_presentation_treenode"));
        assertFalse(result.get(1).renderedContent().contains("cel_cm_presentation_treenode"));
    }

    private void assertOriginalContextsRestored() {
        assertSame(originalContext, execution.getContext().get(XWIKI_CONTEXT).orElseThrow());
        assertSame(originalVelocityContext, execution.getContext().getProperty("velocityContext"));
        assertFalse(originalContext.containsKey("slideLeak"));
        assertFalse(originalVelocityContext.containsKey("slideLeak"));
    }

    private String fixture(String name) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/presentation/" + name)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), UTF_8);
        }
    }
}
