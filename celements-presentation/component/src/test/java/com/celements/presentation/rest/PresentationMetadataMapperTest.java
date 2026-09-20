/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.WikiReference;

import com.celements.model.access.IModelAccessFacade;
import com.celements.model.context.ModelContext;
import com.celements.model.util.ModelUtils;
import com.celements.navigation.INavigation;
import com.celements.navigation.NavigationItemContext;
import com.celements.navigation.NavigationItemContext.ChildState;
import com.celements.navigation.NavigationItemContext.ContainerCssClasses;
import com.celements.navigation.NavigationItemContext.ContextualState;
import com.celements.navigation.NavigationItemContext.Position;
import com.celements.navigation.TreeNode;
import com.celements.pagelayout.LayoutServiceRole;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;

public class PresentationMetadataMapperTest {

    private IModelAccessFacade modelAccess;
    private ModelContext context;
    private ModelUtils modelUtils;
    private LayoutServiceRole layoutService;
    private PresentationSlideListService slideListService;
    private PresentationMetadataMapper mapper;
    private INavigation navigation;
    private DocumentReference firstRef;
    private DocumentReference secondRef;

    @Before
    public void prepareTest() {
        modelAccess = createMock(IModelAccessFacade.class);
        context = createMock(ModelContext.class);
        modelUtils = createMock(ModelUtils.class);
        layoutService = createMock(LayoutServiceRole.class);
        slideListService = createMock(PresentationSlideListService.class);
        mapper = new PresentationMetadataMapper(modelAccess, context, modelUtils, layoutService, slideListService) {

            @Override
            String resolveMenuLabel(String fullName, String language) {
                return language + ":" + fullName;
            }
        };
        navigation = createMock(INavigation.class);
        firstRef = new DocumentReference("xwiki", "Content", "First");
        secondRef = new DocumentReference("xwiki", "Content", "Second");
    }

    @Test
    public void test_map_resolvesEffectiveLanguageMetadataAndFilteredPositions() {
        XWikiContext xwikiContext = new XWikiContext();
        xwikiContext.setLanguage("de");
        expect(context.getXWikiContext()).andReturn(xwikiContext).times(2);
        XWikiDocument firstDoc = new XWikiDocument(firstRef);
        firstDoc.setTitle("Erste");
        XWikiDocument secondDoc = new XWikiDocument(secondRef);
        secondDoc.setTitle("Zweite");
        expect(modelAccess.getDocumentOpt(firstRef, "de")).andReturn(Optional.of(firstDoc));
        expect(modelAccess.getDocumentOpt(secondRef, "de")).andReturn(Optional.of(secondDoc));
        expect(modelUtils.serializeRefLocal(firstRef)).andReturn("Content.First");
        expect(modelUtils.serializeRefLocal(secondRef)).andReturn("Content.Second");
        SpaceReference layoutRef = new SpaceReference("Hero-Layout",
                new SpaceReference("Layouts", new WikiReference("xwiki")));
        expect(layoutService.getPageLayoutForDoc(firstRef)).andReturn(layoutRef);
        expect(layoutService.getPageLayoutForDoc(secondRef)).andReturn(null);
        expect(slideListService.isLeaf(firstRef)).andReturn(true);
        expect(slideListService.isLeaf(secondRef)).andReturn(false);
        expect(navigation.getUniqueId(firstRef)).andReturn("N3:Content:Content.First");
        expect(navigation.getUniqueId(secondRef)).andReturn("N3:Content:Content.Second");
        List<String> firstClasses = List.of("first", "cel_nav_odd", "cel_nav_item1");
        List<String> secondClasses = List.of("last", "cel_nav_even", "cel_nav_item2");
        expect(navigation.getCssClassTokens(new NavigationItemContext(firstRef, ContainerCssClasses.INCLUDE,
                Position.FIRST, ChildState.LEAF, 1, ContextualState.OMIT))).andReturn(firstClasses);
        expect(navigation.getCssClassTokens(new NavigationItemContext(secondRef, ContainerCssClasses.INCLUDE,
                Position.LAST, ChildState.HAS_CHILDREN, 2, ContextualState.OMIT))).andReturn(secondClasses);
        replay(modelAccess, context, modelUtils, layoutService, slideListService, navigation);
        PresentationDefinition definition = new PresentationDefinition(
                new DocumentReference("xwiki", "Config", "WebHome"),
                new SpaceReference("Content", new WikiReference("xwiki")), "", "default", "custom");
        List<SlideResponse> result = mapper.map(definition,
                List.of(new TreeNode(firstRef, null, 41), new TreeNode(secondRef, null, 99)), navigation);
        assertEquals("de:Content.First", result.get(0).menuLabel());
        assertEquals("Erste", result.get(0).title());
        assertEquals("Hero-Layout", result.get(0).pageLayout());
        assertEquals(firstClasses, result.get(0).cssClasses());
        assertEquals("Zweite", result.get(1).title());
        assertNull(result.get(1).pageLayout());
        assertEquals(secondClasses, result.get(1).cssClasses());
        verify(modelAccess, context, modelUtils, layoutService, slideListService, navigation);
    }

}
