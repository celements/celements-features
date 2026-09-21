/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.of;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.same;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.xwiki.cache.Cache;
import org.xwiki.cache.CacheFactory;
import org.xwiki.cache.config.CacheConfiguration;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.context.Execution;
import org.xwiki.model.reference.DocumentReference;

import com.celements.common.classes.IClassCollectionRole;
import com.celements.common.test.AbstractComponentTest;
import com.celements.model.access.IModelAccessFacade;
import com.celements.model.util.ModelUtils;
import com.celements.navigation.INavigation;
import com.celements.navigation.presentation.PresentationContentRenderer;
import com.celements.pagetype.service.IPageTypeResolverRole;
import com.celements.pagetype.service.IPageTypeRole;
import com.celements.rendering.RenderCommand;
import com.celements.web.classcollections.DocumentDetailsClasses;
import com.celements.web.service.IWebUtilsService;
import com.google.common.base.Optional;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiConfig;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.user.api.XWikiRightService;

public class PresentationRendererFidelityTest extends AbstractComponentTest {

    private IModelAccessFacade modelAccess;
    private IPageTypeResolverRole pageTypeResolver;
    private IWebUtilsService webUtilsService;
    private XWikiRightService rightService;
    private ModelUtils modelUtils;
    private INavigation navigation;
    private PresentationBatchRenderer batchRenderer;
    private DocumentReference firstRef;
    private DocumentReference secondRef;
    private PresentationDefinition definition;

    @Before
    public void prepareTest() throws Exception {
        registerComponentMocks(IModelAccessFacade.class, IPageTypeResolverRole.class, IPageTypeRole.class,
                IWebUtilsService.class);
        modelAccess = getMock(IModelAccessFacade.class);
        pageTypeResolver = getMock(IPageTypeResolverRole.class);
        webUtilsService = getMock(IWebUtilsService.class);
        rightService = createDefaultMock(XWikiRightService.class);
        modelUtils = createDefaultMock(ModelUtils.class);
        navigation = createDefaultMock(INavigation.class);
        ComponentManager componentManager = getBeanFactory().getBean(ComponentManager.class);
        Execution execution = getBeanFactory().getBean(Execution.class);
        batchRenderer = new PresentationBatchRenderer(componentManager, modelUtils, execution);
        firstRef = new DocumentReference(DEFAULT_DB, "Content", "First");
        secondRef = new DocumentReference(DEFAULT_DB, "Content", "Second");
        definition = new PresentationDefinition(new DocumentReference(DEFAULT_DB, "Config", "WebHome"),
                firstRef.getLastSpaceReference(), "", "default", "presentation");
        getXContext().setDoc(new XWikiDocument(new DocumentReference(DEFAULT_DB, "Content", "WebHome")));
        expect(getMock(XWiki.class).getRightService()).andReturn(rightService).anyTimes();
    }

    @Test
    public void test_renderedContent_realRenderCommandRendersRichDocuments() throws Exception {
        String firstHtml = fixture("rendered-content-first.html");
        String secondHtml = fixture("rendered-content-second.html");
        XWikiDocument firstDoc = contentDocument(firstRef,
                firstHtml.replace("</section>", "<span data-document=\"$celldoc.fullName\"></span></section>"));
        XWikiDocument secondDoc = contentDocument(secondRef,
                secondHtml.replace("</video>", "</video><span data-document=\"$celldoc.fullName\"></span>"));
        expectContentDocument(firstRef, firstDoc);
        expectContentDocument(secondRef, secondDoc);
        expect(rightService.hasAccessLevel(eq("view"), eq("XWiki.XWikiGuest"), eq("xwikidb:Content.First"),
                anyObject(XWikiContext.class))).andReturn(true);
        expect(rightService.hasAccessLevel(eq("view"), eq("XWiki.XWikiGuest"), eq("xwikidb:Content.Second"),
                anyObject(XWikiContext.class))).andReturn(true);
        expectResponseMetadata();
        RenderingCacheMocks cacheMocks = expectRenderingEngineInitialization();
        replayDefault(cacheMocks.factory(), cacheMocks.cache());

        PresentationContentRenderer renderer = getBeanFactory().getBean("renderedContent",
                PresentationContentRenderer.class);
        ReflectionTestUtils.setField(renderer, "renderCmd", realRenderCommand());
        List<RenderedSlideResponse> result = batchRenderer.render(definition, List.of(firstRef, secondRef), navigation,
                PresentationRenderType.RENDERED_CONTENT);

        verifyDefault(cacheMocks.factory(), cacheMocks.cache());
        assertEquals("Content.First", result.get(0).fullName());
        assertTrue(result.get(0).renderedContent().contains("data-document=\"Content.First\""));
        assertTrue(result.get(0).renderedContent().contains("/xwiki/bin/download/Content/First/guide.pdf"));
        assertTrue(result.get(0).renderedContent().contains("data-editorial-crop=\"wide\""));
        assertEquals("Content.Second", result.get(1).fullName());
        assertTrue(result.get(1).renderedContent().contains("data-document=\"Content.Second\""));
        assertTrue(result.get(1).renderedContent().contains("<video"));
        assertFalse(result.get(0).renderedContent().startsWith("<div "));
    }

    @Test
    public void test_renderedExtract_realRenderCommandRendersExtractTemplate() throws Exception {
        String firstHtml = fixture("rendered-extract-first.html");
        String secondHtml = fixture("rendered-extract-second.html");
        XWikiDocument firstDoc = extractDocument(firstRef, firstHtml);
        XWikiDocument secondDoc = extractDocument(secondRef, secondHtml);
        expect(webUtilsService.getInheritedTemplatedPath(anyObject(DocumentReference.class)))
                .andReturn(":celTemplates/RenderedExtract.vm").times(2);
        expect(webUtilsService.getTranslatedDiscTemplateContent(":celTemplates/RenderedExtract.vm", DEFAULT_LANG, ""))
                .andReturn("$extractContent").times(2);
        expect(getMock(XWiki.class).getDocument(eq(firstRef), anyObject(XWikiContext.class))).andReturn(firstDoc)
                .times(2);
        expect(getMock(XWiki.class).getDocument(eq(secondRef), anyObject(XWikiContext.class))).andReturn(secondDoc)
                .times(2);
        expectResponseMetadata();
        RenderingCacheMocks cacheMocks = expectRenderingEngineInitialization();
        replayDefault(cacheMocks.factory(), cacheMocks.cache());

        PresentationContentRenderer renderer = getBeanFactory().getBean("renderedExtract",
                PresentationContentRenderer.class);
        ReflectionTestUtils.setField(renderer, "renderCmd", realRenderCommand());
        List<RenderedSlideResponse> result = batchRenderer.render(definition, List.of(firstRef, secondRef), navigation,
                PresentationRenderType.RENDERED_EXTRACT);

        verifyDefault(cacheMocks.factory(), cacheMocks.cache());
        assertEquals(firstHtml, result.get(0).renderedContent());
        assertTrue(result.get(0).renderedContent().contains("<strong>semantic emphasis</strong>"));
        assertTrue(result.get(0).renderedContent().contains("data-editorial-focus=\"center\""));
        assertEquals(secondHtml, result.get(1).renderedContent());
        assertTrue(result.get(1).renderedContent().contains("/xwiki/bin/download/Content/Second/factsheet.pdf"));
        assertTrue(result.get(1).renderedContent().contains("<aside"));
        assertFalse(result.get(0).renderedContent().startsWith("<div "));
    }

    private void expectContentDocument(DocumentReference ref, XWikiDocument doc) throws Exception {
        expect(pageTypeResolver.resolvePageTypeReference(same(doc))).andReturn(Optional.absent());
        expect(modelAccess.getOrCreateDocument(eq(ref))).andReturn(doc);
        expect(modelAccess.getDocumentOpt(eq(ref))).andReturn(of(doc));
    }

    private XWikiDocument contentDocument(DocumentReference ref, String content) {
        XWikiDocument doc = new XWikiDocument(ref);
        doc.setDefaultLanguage(DEFAULT_LANG);
        doc.setLanguage(DEFAULT_LANG);
        doc.setContent(content);
        return doc;
    }

    private XWikiDocument extractDocument(DocumentReference ref, String content) throws Exception {
        DocumentDetailsClasses classes = (DocumentDetailsClasses) getBeanFactory().getBean("celements.documentDetails",
                IClassCollectionRole.class);
        XWikiDocument doc = new XWikiDocument(ref);
        BaseObject extract = new BaseObject();
        extract.setXClassReference(classes.getDocumentExtractClassRef(DEFAULT_DB));
        extract.setStringValue(DocumentDetailsClasses.FIELD_DOC_EXTRACT_LANGUAGE, DEFAULT_LANG);
        extract.setStringValue(DocumentDetailsClasses.FIELD_DOC_EXTRACT_CONTENT, content);
        doc.addXObject(extract);
        return doc;
    }

    private void expectResponseMetadata() {
        expect(modelUtils.serializeRefLocal(firstRef)).andReturn("Content.First");
        expect(navigation.getUniqueId(firstRef)).andReturn("N1:Content:Content.First");
        expect(modelUtils.serializeRefLocal(secondRef)).andReturn("Content.Second");
        expect(navigation.getUniqueId(secondRef)).andReturn("N1:Content:Content.Second");
    }

    @SuppressWarnings("unchecked")
    private RenderingCacheMocks expectRenderingEngineInitialization() throws Exception {
        XWiki xwiki = getMock(XWiki.class);
        expect(xwiki.Param("xwiki.render.velocity.macrolist")).andReturn("").anyTimes();
        expect(xwiki.Param(anyString())).andReturn(null).anyTimes();
        expect(xwiki.Param(isA(String.class), eq("0"))).andReturn("0").anyTimes();
        expect(xwiki.Param(isA(String.class), eq("1"))).andReturn("1").anyTimes();
        expect(xwiki.Param(eq("xwiki.render.cache.capacity"))).andReturn(null).anyTimes();
        expect(xwiki.ParamAsLong("xwiki.rendering.defaultCacheDuration", 0L)).andReturn(0L).anyTimes();
        expect(xwiki.getXWikiPreference(eq("macros_languages"), eq("velocity,groovy"), anyObject(XWikiContext.class)))
                .andReturn("velocity,groovy").anyTimes();
        expect(xwiki.getXWikiPreference(eq("macros_velocity"), eq("XWiki.VelocityMacros"),
                anyObject(XWikiContext.class))).andReturn("XWiki.VelocityMacros").anyTimes();
        expect(xwiki.getXWikiPreference(eq("macros_groovy"), eq("XWiki.GroovyMacros"), anyObject(XWikiContext.class)))
                .andReturn("XWiki.GroovyMacros").anyTimes();
        expect(xwiki.getMacroList(anyObject(XWikiContext.class))).andReturn("").anyTimes();
        expect(xwiki.getSpacePreference(eq("renderXWikiVelocityRenderer"), anyObject())).andReturn("1").anyTimes();
        expect(xwiki.getIncludedMacros(anyString(), anyString(), anyObject(XWikiContext.class))).andReturn(List.of())
                .anyTimes();
        expect(xwiki.getSkin(anyObject(XWikiContext.class))).andReturn("").anyTimes();
        expect(xwiki.getSkinFile(eq("macros.vm"), eq(""), anyObject(XWikiContext.class))).andReturn("").anyTimes();
        expect(xwiki.getPluginManager()).andReturn(null).anyTimes();
        CacheFactory cacheFactory = createMock(CacheFactory.class);
        Cache<Object> cache = createMock(Cache.class);
        expect(xwiki.getCacheFactory()).andReturn(cacheFactory);
        expect(cacheFactory.newCache(anyObject(CacheConfiguration.class))).andReturn(cache);
        expect(cache.get(anyString())).andReturn(null).anyTimes();
        cache.set(anyString(), anyObject());
        expectLastCall().anyTimes();
        expect(xwiki.getConfig()).andReturn(new XWikiConfig());
        return new RenderingCacheMocks(cacheFactory, cache);
    }

    private RenderCommand realRenderCommand() throws Exception {
        RenderCommand renderCommand = new RenderCommand();
        renderCommand.setRenderingEngine(renderCommand.initRenderingEngine(List.of("velocity")));
        return renderCommand;
    }

    private String fixture(String name) throws IOException {
        try (InputStream stream = requireNonNull(getClass().getResourceAsStream("/presentation/" + name))) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record RenderingCacheMocks(CacheFactory factory, Cache<Object> cache) {
    }

}
