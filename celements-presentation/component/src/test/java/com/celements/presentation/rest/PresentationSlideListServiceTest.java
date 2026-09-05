/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.Before;
import org.junit.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.WikiReference;

import com.celements.model.access.IModelAccessFacade;
import com.celements.navigation.TreeNode;
import com.celements.navigation.filter.INavFilter;
import com.celements.navigation.service.ITreeNodeService;

public class PresentationSlideListServiceTest {

    private IModelAccessFacade modelAccess;
    private ITreeNodeService treeNodeService;
    private PresentationSlideListService service;
    private SpaceReference menuSpace;
    private PresentationDefinition definition;

    @Before
    public void setUp() {
        modelAccess = createMock(IModelAccessFacade.class);
        treeNodeService = createMock(ITreeNodeService.class);
        service = new PresentationSlideListService(modelAccess, treeNodeService);
        menuSpace = new SpaceReference("Content", new WikiReference("xwiki"));
        definition = new PresentationDefinition(new DocumentReference("xwiki", "Config", "WebHome"), menuSpace,
                "homepage", "default", "custom");
    }

    @Test
    public void getVisibleSlides_honorsSpaceAndPartPreservesOrderAndDropsUnavailable() {
        DocumentReference firstRef = new DocumentReference("xwiki", "Content", "First");
        DocumentReference missingRef = new DocumentReference("xwiki", "Content", "Missing");
        DocumentReference lastRef = new DocumentReference("xwiki", "Content", "Last");
        TreeNode first = new TreeNode(firstRef, null, 1);
        TreeNode missing = new TreeNode(missingRef, null, 2);
        TreeNode last = new TreeNode(lastRef, null, 3);
        Capture<INavFilter<Object>> filterCapture = Capture.newInstance(CaptureType.FIRST);
        expect(treeNodeService.getSubNodesForParent(eq(menuSpace), capture(filterCapture)))
                .andReturn(List.of(first, missing, last));
        expect(modelAccess.exists(firstRef)).andReturn(true);
        expect(modelAccess.exists(missingRef)).andReturn(false);
        expect(modelAccess.exists(lastRef)).andReturn(true);
        replay(modelAccess, treeNodeService);
        List<TreeNode> result = service.getVisibleSlides(definition);
        assertEquals("homepage", filterCapture.getValue().getMenuPart());
        assertEquals(2, result.size());
        assertSame(first, result.get(0));
        assertSame(last, result.get(1));
        verify(modelAccess, treeNodeService);
    }

    @Test
    public void isLeaf_checksRightsVisibleChildrenWithBlankPart() {
        DocumentReference slideRef = new DocumentReference("xwiki", "Content", "Slide");
        DocumentReference childRef = new DocumentReference("xwiki", "Content", "Child");
        Capture<INavFilter<Object>> filterCapture = Capture.newInstance(CaptureType.FIRST);
        expect(treeNodeService.getSubNodesForParent(eq(slideRef), capture(filterCapture)))
                .andReturn(List.of(new TreeNode(childRef, slideRef, 1)));
        expect(modelAccess.exists(childRef)).andReturn(true);
        replay(modelAccess, treeNodeService);
        assertFalse(service.isLeaf(slideRef));
        assertEquals("", filterCapture.getValue().getMenuPart());
        verify(modelAccess, treeNodeService);
    }

    @Test
    public void validateSelection_rejectsWholeBatchWhenOneSlideIsNotMember() {
        DocumentReference memberRef = new DocumentReference("xwiki", "Content", "Member");
        DocumentReference outsiderRef = new DocumentReference("xwiki", "Content", "Outsider");
        try {
            service.validateSelection(definition, List.of(new TreeNode(memberRef, null, 1)),
                    List.of(memberRef, outsiderRef));
            fail("Expected slide_not_found");
        } catch (PresentationException exc) {
            assertEquals("slide_not_found", exc.getCode());
            assertTrue(exc.getDiagnosticContext().contains("WebHome"));
            assertTrue(exc.getDiagnosticContext().contains("Outsider"));
        }
    }

}
