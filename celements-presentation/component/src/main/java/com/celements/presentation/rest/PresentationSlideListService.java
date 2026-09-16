/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.springframework.stereotype.Component;
import org.xwiki.model.reference.DocumentReference;

import com.celements.model.access.IModelAccessFacade;
import com.celements.navigation.TreeNode;
import com.celements.navigation.filter.InternalRightsFilter;
import com.celements.navigation.service.ITreeNodeService;

@Component
public class PresentationSlideListService {

    private final IModelAccessFacade modelAccess;
    private final ITreeNodeService treeNodeService;

    @Inject
    public PresentationSlideListService(IModelAccessFacade modelAccess, ITreeNodeService treeNodeService) {
        this.modelAccess = modelAccess;
        this.treeNodeService = treeNodeService;
    }

    public List<TreeNode> getVisibleSlides(PresentationDefinition definition) {
        InternalRightsFilter filter = new InternalRightsFilter();
        filter.setMenuPart(definition.menuPart());
        return treeNodeService.getSubNodesForParent(definition.menuSpace(), filter).stream()
                .filter(node -> modelAccess.exists(node.getDocumentReference())).toList();
    }

    public boolean isLeaf(DocumentReference slideRef) {
        InternalRightsFilter childFilter = new InternalRightsFilter();
        childFilter.setMenuPart("");
        return treeNodeService.getSubNodesForParent(slideRef, childFilter).stream()
                .noneMatch(node -> modelAccess.exists(node.getDocumentReference()));
    }

    public void validateSelection(PresentationDefinition definition, List<TreeNode> slides,
            List<DocumentReference> requestedSlides) {
        Set<DocumentReference> members = new HashSet<>();
        slides.forEach(node -> members.add(node.getDocumentReference()));
        List<DocumentReference> missingSlides = requestedSlides.stream().filter(slide -> !members.contains(slide))
                .toList();
        if (!missingSlides.isEmpty()) {
            throw PresentationException.notFound("slide_not_found",
                    "Presentation [" + PresentationDiagnostic.safeReference(definition.configRef())
                            + "] has no visible direct members "
                            + PresentationDiagnostic.safeReferences(missingSlides));
        }
    }

}
