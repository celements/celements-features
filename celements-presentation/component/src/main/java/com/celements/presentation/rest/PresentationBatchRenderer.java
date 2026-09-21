/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import static com.celements.execution.XWikiExecutionProp.XWIKI_CONTEXT;

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
        } catch (ComponentLookupException | RuntimeException | XWikiException exc) {
            String diagnostic = "Failed presentation [" + PresentationDiagnostic.safeReference(definition.configRef())
                    + "] slide [" + PresentationDiagnostic.safeReference(failingSlide) + "] renderer hint ["
                    + renderType.componentHint() + "]";
            throw PresentationException.renderingFailed(diagnostic, exc);
        }
    }

    private String renderIsolated(PresentationContentRenderer renderer, DocumentReference slideRef)
            throws XWikiException {
        ExecutionContext executionContext = execution.getContext();
        XWikiContext originalContext = executionContext.get(XWIKI_CONTEXT).orElseThrow();
        Object originalVelocityProperty = executionContext.getProperty("velocityContext");
        XWikiContext isolatedContext = originalContext.clone();
        VelocityContext isolatedVelocityContext = cloneVelocityContext(originalContext);
        isolatedContext.put("vcontext", isolatedVelocityContext);
        try {
            executionContext.setProperty(XWIKI_CONTEXT.getName(), isolatedContext);
            executionContext.setProperty("velocityContext", isolatedVelocityContext);
            return renderer.renderInnerContent(slideRef);
        } finally {
            executionContext.setProperty(XWIKI_CONTEXT.getName(), originalContext);
            if (originalVelocityProperty != null) {
                executionContext.setProperty("velocityContext", originalVelocityProperty);
            } else {
                executionContext.removeProperty("velocityContext");
            }
        }
    }

    private VelocityContext cloneVelocityContext(XWikiContext context) {
        Object velocityContext = context.get("vcontext");
        return velocityContext instanceof VelocityContext existing ? (VelocityContext) existing.clone()
                : new VelocityContext();
    }

}
