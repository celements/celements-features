/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import static com.celements.execution.XWikiExecutionProp.XWIKI_CONTEXT;
import static org.xwiki.velocity.VelocityExecutionProp.VELOCITY_CONTEXT;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.velocity.VelocityContext;
import org.springframework.stereotype.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.velocity.VelocityContextFactory;
import org.xwiki.velocity.VelocityManager;
import org.xwiki.velocity.XWikiVelocityException;

import com.celements.model.util.ModelUtils;
import com.celements.navigation.INavigation;
import com.celements.navigation.presentation.PresentationContentRenderer;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;

@Component
public class PresentationBatchRenderer {

    private final ComponentManager componentManager;
    private final ModelUtils modelUtils;
    private final Execution execution;

    @Inject
    public PresentationBatchRenderer(ComponentManager componentManager, ModelUtils modelUtils, Execution execution) {
        this.componentManager = componentManager;
        this.modelUtils = modelUtils;
        this.execution = execution;
    }

    public List<RenderedSlideResponse> render(PresentationDefinition definition, List<DocumentReference> slideRefs,
            INavigation navigation, PresentationRenderType renderType) {
        List<RenderedSlideResponse> renderedSlides = new ArrayList<>();
        DocumentReference failingSlide = null;
        try {
            PresentationContentRenderer renderer = componentManager.lookup(PresentationContentRenderer.class,
                    renderType.componentHint());
            for (DocumentReference slideRef : slideRefs) {
                failingSlide = slideRef;
                renderedSlides.add(new RenderedSlideResponse(modelUtils.serializeRefLocal(slideRef),
                        navigation.getUniqueId(slideRef), renderIsolated(renderer, slideRef)));
            }
            return List.copyOf(renderedSlides);
        } catch (ComponentLookupException | RuntimeException | XWikiException | XWikiVelocityException exc) {
            String diagnostic = "Failed presentation [" + PresentationDiagnostic.safeReference(definition.configRef())
                    + "] slide [" + PresentationDiagnostic.safeReference(failingSlide) + "] renderer hint ["
                    + renderType.componentHint() + "]";
            throw PresentationException.renderingFailed(diagnostic, exc);
        }
    }

    private String renderIsolated(PresentationContentRenderer renderer, DocumentReference slideRef)
            throws ComponentLookupException, XWikiException, XWikiVelocityException {
        ExecutionContext original = execution.getContext();
        XWikiContext isolatedXWiki = original.get(XWIKI_CONTEXT).orElseThrow().clone();
        ExecutionContext isolated = new ExecutionContext();
        isolated.setProperties(original.getProperties());
        isolated.set(XWIKI_CONTEXT, isolatedXWiki);
        execution.pushContext(isolated);
        try {
            isolated.set(VELOCITY_CONTEXT, isolateVelocityContext(original, isolatedXWiki));
            // RenderCommand requires the alias, which the manager sets after rebinding the legacy wrappers.
            componentManager.lookup(VelocityManager.class).getVelocityContext();
            return renderer.renderInnerContent(slideRef);
        } finally {
            execution.popContext();
        }
    }

    private VelocityContext isolateVelocityContext(ExecutionContext original, XWikiContext isolatedXWiki)
            throws ComponentLookupException, XWikiVelocityException {
        VelocityContext existing = original.get(VELOCITY_CONTEXT).orElse(null);
        if (existing == null && isolatedXWiki.get("vcontext") instanceof VelocityContext legacy) {
            existing = legacy;
        }
        return existing != null ? (VelocityContext) existing.clone()
                : componentManager.lookup(VelocityContextFactory.class).createContext();
    }

}
