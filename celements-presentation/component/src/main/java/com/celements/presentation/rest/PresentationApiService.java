/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import java.util.List;

import javax.inject.Inject;

import org.springframework.stereotype.Component;
import org.xwiki.model.reference.DocumentReference;

import com.celements.model.context.ModelContext;
import com.celements.model.util.ModelUtils;
import com.celements.navigation.INavigation;
import com.celements.navigation.TreeNode;

@Component
public class PresentationApiService {

    private final ModelUtils modelUtils;
    private final ModelContext context;
    private final PresentationBatchRenderer batchRenderer;
    private final PresentationRequestResolver requestResolver;
    private final PresentationConfigReader configReader;
    private final PresentationSlideListService slideListService;
    private final PresentationMetadataMapper metadataMapper;

    @Inject
    public PresentationApiService(ModelUtils modelUtils, ModelContext context, PresentationBatchRenderer batchRenderer,
            PresentationRequestResolver requestResolver, PresentationConfigReader configReader,
            PresentationSlideListService slideListService, PresentationMetadataMapper metadataMapper) {
        this.modelUtils = modelUtils;
        this.context = context;
        this.batchRenderer = batchRenderer;
        this.requestResolver = requestResolver;
        this.configReader = configReader;
        this.slideListService = slideListService;
        this.metadataMapper = metadataMapper;
    }

    public PresentationResponse getPresentation(List<String> configNames, int navigationNumber) {
        requestResolver.validateNavigationNumber(navigationNumber);
        PresentationData presentation = loadPresentation(requestResolver.resolvePresentationReference(configNames),
                navigationNumber);
        return new PresentationResponse(modelUtils.serializeRefLocal(presentation.definition().configRef()),
                presentation.definition().presentationType(), context.getXWikiContext().getLanguage(),
                metadataMapper.map(presentation.definition(), presentation.slides(), presentation.navigation()));
    }

    public RenderedPresentationResponse renderSlides(List<String> configNames, List<String> slideNames,
            List<String> renderTypes, int navigationNumber) {
        requestResolver.validateNavigationNumber(navigationNumber);
        DocumentReference configRef = requestResolver.resolvePresentationReference(configNames);
        List<DocumentReference> requestedSlides = requestResolver.resolveSlideReferences(slideNames);
        PresentationRenderType renderType = requestResolver.resolveRenderType(renderTypes);
        PresentationData presentation = loadPresentation(configRef, navigationNumber);
        slideListService.validateSelection(presentation.definition(), presentation.slides(), requestedSlides);
        return new RenderedPresentationResponse(context.getXWikiContext().getLanguage(), batchRenderer
                .render(presentation.definition(), requestedSlides, presentation.navigation(), renderType));
    }

    private PresentationData loadPresentation(DocumentReference configRef, int navigationNumber) {
        PresentationDefinition definition = configReader.read(configRef);
        List<TreeNode> slides = slideListService.getVisibleSlides(definition);
        INavigation navigation = metadataMapper.createNavigation(definition, navigationNumber);
        return new PresentationData(definition, navigation, slides);
    }

    private record PresentationData(PresentationDefinition definition, INavigation navigation, List<TreeNode> slides) {
    }

}
