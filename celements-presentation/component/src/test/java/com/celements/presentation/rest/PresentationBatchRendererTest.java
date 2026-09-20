/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import static com.celements.execution.XWikiExecutionProp.XWIKI_CONTEXT;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.velocity.VelocityContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.model.reference.DocumentReference;

import com.celements.model.util.ModelUtils;
import com.celements.navigation.INavigation;
import com.celements.navigation.presentation.PresentationContentRenderer;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;

@RunWith(Parameterized.class)
public class PresentationBatchRendererTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> renderTypes() {
        return List.of(new Object[] { PresentationRenderType.RENDERED_CONTENT },
                new Object[] { PresentationRenderType.RENDERED_EXTRACT });
    }

    private final PresentationRenderType renderType;
    private ComponentManager componentManager;
    private ModelUtils modelUtils;
    private INavigation navigation;
    private PresentationContentRenderer renderer;
    private Execution execution;
    private PresentationBatchRenderer batchRenderer;
    private DocumentReference firstRef;
    private DocumentReference secondRef;
    private ExecutionContext executionContext;
    private XWikiContext originalContext;
    private VelocityContext originalVelocityContext;
    private PresentationDefinition definition;

    public PresentationBatchRendererTest(PresentationRenderType renderType) {
        this.renderType = renderType;
    }

    @Before
    public void prepareTest() {
        componentManager = createMock(ComponentManager.class);
        modelUtils = createMock(ModelUtils.class);
        navigation = createMock(INavigation.class);
        renderer = createMock(PresentationContentRenderer.class);
        execution = createMock(Execution.class);
        executionContext = new ExecutionContext();
        originalContext = new XWikiContext();
        originalVelocityContext = new VelocityContext();
        originalContext.put("vcontext", originalVelocityContext);
        executionContext.setProperty(XWIKI_CONTEXT.getName(), originalContext);
        executionContext.setProperty("velocityContext", originalVelocityContext);
        expect(execution.getContext()).andReturn(executionContext).times(2);
        replay(execution);
        batchRenderer = new PresentationBatchRenderer(componentManager, modelUtils, execution);
        firstRef = new DocumentReference("xwiki", "Content", "First");
        secondRef = new DocumentReference("xwiki", "Content", "Second");
        definition = new PresentationDefinition(new DocumentReference("xwiki", "Config", "WebHome"),
                firstRef.getLastSpaceReference(), "", "default", "presentation");
    }

    @Test
    public void test_render_isolatesFullContextForEverySlideAndPreservesRequestOrder() throws Exception {
        expect(componentManager.lookup(PresentationContentRenderer.class, renderType.componentHint()))
                .andReturn(renderer);
        expect(modelUtils.serializeRefLocal(firstRef)).andReturn("Content.First");
        expect(navigation.getUniqueId(firstRef)).andReturn("N1:Content:Content.First");
        expect(modelUtils.serializeRefLocal(secondRef)).andReturn("Content.Second");
        expect(navigation.getUniqueId(secondRef)).andReturn("N1:Content:Content.Second");
        AtomicInteger invocation = new AtomicInteger();
        expect(renderer.renderInnerContent(firstRef)).andAnswer(() -> renderAndMutate(invocation));
        expect(renderer.renderInnerContent(secondRef)).andAnswer(() -> renderAndMutate(invocation));
        replay(componentManager, modelUtils, navigation, renderer);
        List<RenderedSlideResponse> result = batchRenderer.render(definition, List.of(firstRef, secondRef), navigation,
                renderType);
        assertEquals(List.of(new RenderedSlideResponse("Content.First", "N1:Content:Content.First", "slide 1"),
                new RenderedSlideResponse("Content.Second", "N1:Content:Content.Second", "slide 2")), result);
        assertSame(originalContext, executionContext.getProperty(XWIKI_CONTEXT.getName()));
        assertSame(originalVelocityContext, executionContext.getProperty("velocityContext"));
        assertFalse(originalContext.containsKey("slideLeak"));
        assertFalse(originalVelocityContext.containsKey("slideLeak"));
        verify(componentManager, modelUtils, navigation, renderer, execution);
    }

    @Test
    public void test_render_failureIsAtomicAndRestoresContexts() throws Exception {
        expect(componentManager.lookup(PresentationContentRenderer.class, renderType.componentHint()))
                .andReturn(renderer);
        expect(modelUtils.serializeRefLocal(firstRef)).andReturn("Content.First");
        expect(navigation.getUniqueId(firstRef)).andReturn("N1:Content:Content.First");
        expect(renderer.renderInnerContent(firstRef)).andReturn("first html");
        expect(modelUtils.serializeRefLocal(secondRef)).andReturn("Content.Second");
        expect(navigation.getUniqueId(secondRef)).andReturn("N1:Content:Content.Second");
        XWikiException failure = new XWikiException();
        expect(renderer.renderInnerContent(secondRef)).andThrow(failure);
        replay(componentManager, modelUtils, navigation, renderer);
        try {
            batchRenderer.render(definition, List.of(firstRef, secondRef), navigation, renderType);
            fail("Expected rendering_failed");
        } catch (PresentationException exc) {
            assertEquals("rendering_failed", exc.getCode());
            assertSame(failure, exc.getCause());
            assertTrue(exc.getDiagnosticContext().contains("WebHome"));
            assertTrue(exc.getDiagnosticContext().contains("Second"));
        }
        assertSame(originalContext, executionContext.getProperty(XWIKI_CONTEXT.getName()));
        assertSame(originalVelocityContext, executionContext.getProperty("velocityContext"));
        verify(componentManager, modelUtils, navigation, renderer, execution);
    }

    private String renderAndMutate(AtomicInteger invocation) {
        int slideNumber = invocation.incrementAndGet();
        XWikiContext isolatedContext = (XWikiContext) executionContext.getProperty(XWIKI_CONTEXT.getName());
        VelocityContext isolatedVelocity = (VelocityContext) executionContext.getProperty("velocityContext");
        assertNotSame(originalContext, isolatedContext);
        assertNotSame(originalVelocityContext, isolatedVelocity);
        assertFalse(isolatedContext.containsKey("slideLeak"));
        assertFalse(isolatedVelocity.containsKey("slideLeak"));
        isolatedContext.put("slideLeak", slideNumber);
        isolatedVelocity.put("slideLeak", slideNumber);
        return "slide " + slideNumber;
    }

}
