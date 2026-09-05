/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.springframework.stereotype.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;

import com.celements.model.access.IModelAccessFacade;
import com.celements.model.context.ModelContext;
import com.celements.model.util.ModelUtils;
import com.celements.navigation.INavigation;
import com.celements.navigation.Navigation;
import com.celements.navigation.NavigationItemContext;
import com.celements.navigation.NavigationItemContext.ChildState;
import com.celements.navigation.NavigationItemContext.ContainerCssClasses;
import com.celements.navigation.NavigationItemContext.ContextualState;
import com.celements.navigation.NavigationItemContext.Position;
import com.celements.navigation.TreeNode;
import com.celements.navigation.cmd.MultilingualMenuNameCommand;
import com.celements.navigation.filter.InternalRightsFilter;
import com.celements.pagelayout.LayoutServiceRole;
import com.xpn.xwiki.doc.XWikiDocument;

@Component
public class PresentationMetadataMapper {

    private final IModelAccessFacade modelAccess;
    private final ModelContext context;
    private final ModelUtils modelUtils;
    private final LayoutServiceRole layoutService;
    private final PresentationSlideListService slideListService;

    @Inject
    public PresentationMetadataMapper(IModelAccessFacade modelAccess, ModelContext context, ModelUtils modelUtils,
            LayoutServiceRole layoutService, PresentationSlideListService slideListService) {
        this.modelAccess = modelAccess;
        this.context = context;
        this.modelUtils = modelUtils;
        this.layoutService = layoutService;
        this.slideListService = slideListService;
    }

    public List<SlideResponse> map(PresentationDefinition definition, List<TreeNode> slides, INavigation navigation) {
        List<SlideResponse> responses = new ArrayList<>();
        int size = slides.size();
        for (int index = 0; index < size; index++) {
            DocumentReference slideRef = slides.get(index).getDocumentReference();
            int position = index + 1;
            SpaceReference layoutRef = layoutService.getPageLayoutForDoc(slideRef);
            String pageLayout = layoutRef == null ? null : layoutRef.getName();
            String language = context.getXWikiContext().getLanguage();
            XWikiDocument slideDoc = modelAccess.getDocumentOpt(slideRef, language)
                    .orElseGet(() -> modelAccess.getDocumentOpt(slideRef)
                            .orElseThrow(() -> PresentationException.notFound("slide_not_found",
                                    "Visible presentation member became unavailable during metadata mapping")));
            String fullName = modelUtils.serializeRefLocal(slideRef);
            NavigationItemContext itemContext = new NavigationItemContext(slideRef,
                    ContainerCssClasses.INCLUDE, getPosition(index, size),
                    slideListService.isLeaf(slideRef) ? ChildState.LEAF : ChildState.HAS_CHILDREN,
                    position, ContextualState.OMIT);
            responses.add(new SlideResponse(fullName, slideRef.getName(), resolveMenuLabel(fullName, language),
                    slideDoc.getTitle(), navigation.getUniqueId(slideRef), pageLayout,
                    navigation.getCssClassTokens(itemContext)));
        }
        return List.copyOf(responses);
    }

    public INavigation createNavigation(PresentationDefinition definition, int navigationNumber) {
        InternalRightsFilter filter = new InternalRightsFilter();
        filter.setMenuPart(definition.menuPart());
        INavigation navigation = new Navigation("N" + navigationNumber);
        navigation.setNodeSpace(definition.menuSpace());
        navigation.setNavFilter(filter);
        navigation.setCMcssClass(definition.cssClass());
        return navigation;
    }

    String resolveMenuLabel(String fullName, String language) {
        return new MultilingualMenuNameCommand().getMultilingualMenuName(fullName, language, context.getXWikiContext());
    }

    private Position getPosition(int index, int size) {
        if (size == 1) {
            return Position.ONLY;
        } else if (index == 0) {
            return Position.FIRST;
        } else if (index == size - 1) {
            return Position.LAST;
        } else {
            return Position.MIDDLE;
        }
    }

}
