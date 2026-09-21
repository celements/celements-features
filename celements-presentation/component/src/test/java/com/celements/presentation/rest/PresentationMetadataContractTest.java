/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.WikiReference;

import com.celements.common.test.AbstractComponentTest;
import com.celements.model.access.IModelAccessFacade;
import com.celements.model.context.ModelContext;
import com.celements.model.util.ModelUtils;
import com.celements.navigation.INavigation;
import com.celements.navigation.Navigation;
import com.celements.navigation.TreeNode;
import com.celements.pagelayout.LayoutServiceRole;
import com.celements.pagetype.PageTypeReference;
import com.celements.pagetype.service.IPageTypeResolverRole;
import com.celements.web.service.IWebUtilsService;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.user.api.XWikiRightService;

public class PresentationMetadataContractTest extends AbstractComponentTest {

    private IModelAccessFacade modelAccess;
    private ModelContext modelContext;
    private ModelUtils modelUtils;
    private LayoutServiceRole layoutService;
    private PresentationSlideListService slideListService;
    private IPageTypeResolverRole pageTypeResolver;
    private XWikiRightService rightService;
    private PresentationMetadataMapper mapper;
    private DocumentReference firstRef;
    private DocumentReference secondRef;

    @Before
    public void prepareTest() throws Exception {
        registerComponentMocks(IModelAccessFacade.class, ModelContext.class, ModelUtils.class, LayoutServiceRole.class,
                IPageTypeResolverRole.class, IWebUtilsService.class);
        modelAccess = getMock(IModelAccessFacade.class);
        modelContext = getMock(ModelContext.class);
        modelUtils = getMock(ModelUtils.class);
        layoutService = getMock(LayoutServiceRole.class);
        pageTypeResolver = getMock(IPageTypeResolverRole.class);
        IWebUtilsService webUtils = getMock(IWebUtilsService.class);
        slideListService = createDefaultMock(PresentationSlideListService.class);
        mapper = new PresentationMetadataMapper(modelAccess, modelContext, modelUtils, layoutService,
                slideListService) {

            @Override
            String resolveMenuLabel(String fullName, String language) {
                return language + ":" + fullName;
            }
        };
        firstRef = new DocumentReference(DEFAULT_DB, "Content", "First");
        secondRef = new DocumentReference(DEFAULT_DB, "Content", "Second");
        XWikiContext xwikiContext = getXContext();
        xwikiContext.setDoc(new XWikiDocument(firstRef));
        expect(webUtils.getRefLocalSerializer())
                .andReturn(getBeanFactory().getBean("local", EntityReferenceSerializer.class)).anyTimes();
        rightService = createDefaultMock(XWikiRightService.class);
        expect(getMock(XWiki.class).getRightService()).andReturn(rightService).anyTimes();
    }

    @Test
    public void test_map_usesRealNavigationForExactRootMetadata() throws Exception {
        XWikiContext xwikiContext = getXContext();
        expect(modelContext.getXWikiContext()).andReturn(xwikiContext).times(4);
        XWikiDocument firstDoc = new XWikiDocument(firstRef);
        firstDoc.setTitle("First title");
        XWikiDocument secondDoc = new XWikiDocument(secondRef);
        secondDoc.setTitle("Second title");
        expect(modelAccess.getDocumentOpt(firstRef, DEFAULT_LANG)).andReturn(Optional.of(firstDoc)).times(2);
        expect(modelAccess.getDocumentOpt(secondRef, DEFAULT_LANG)).andReturn(Optional.of(secondDoc)).times(2);
        expect(modelUtils.serializeRefLocal(firstRef)).andReturn("Content.First").times(2);
        expect(modelUtils.serializeRefLocal(secondRef)).andReturn("Content.Second").times(2);
        SpaceReference layoutRef = new SpaceReference("About-Layout",
                new SpaceReference("Layouts", new WikiReference(DEFAULT_DB)));
        expect(layoutService.getPageLayoutForDoc(firstRef)).andReturn(layoutRef).times(4);
        expect(layoutService.getPageLayoutForDoc(secondRef)).andReturn(null).times(4);
        PageTypeReference firstPageType = createMock(PageTypeReference.class);
        PageTypeReference secondPageType = createMock(PageTypeReference.class);
        expect(pageTypeResolver.getPageTypeRefForDocWithDefault(firstRef)).andReturn(firstPageType).times(2);
        expect(pageTypeResolver.getPageTypeRefForDocWithDefault(secondRef)).andReturn(secondPageType).times(2);
        expect(firstPageType.getConfigName()).andReturn("FirstPageType").times(2);
        expect(secondPageType.getConfigName()).andReturn("SecondPageType").times(2);
        expect(slideListService.isLeaf(firstRef)).andReturn(true).times(2);
        expect(slideListService.isLeaf(secondRef)).andReturn(false).times(2);
        expect(rightService.hasAccessLevel("view", "XWiki.XWikiGuest", "Content.First", xwikiContext)).andReturn(true)
                .times(2);
        expect(rightService.hasAccessLevel("view", "XWiki.XWikiGuest", "Content.Second", xwikiContext)).andReturn(false)
                .times(2);
        PresentationDefinition defaultDefinition = new PresentationDefinition(
                new DocumentReference(DEFAULT_DB, "Config", "WebHome"),
                new SpaceReference("Content", new WikiReference(DEFAULT_DB)), "homepage", "default", "");
        PresentationDefinition renderedDefinition = new PresentationDefinition(
                new DocumentReference(DEFAULT_DB, "Config", "WebHome"),
                new SpaceReference("Content", new WikiReference(DEFAULT_DB)), "homepage", "renderedContent", "");
        List<TreeNode> slides = List.of(new TreeNode(firstRef, null, 17), new TreeNode(secondRef, null, 42));
        replayDefault(firstPageType, secondPageType);

        INavigation navigation1 = mapper.createNavigation(defaultDefinition, 1);
        INavigation navigation2 = mapper.createNavigation(renderedDefinition, 2);
        List<SlideResponse> response1 = mapper.map(defaultDefinition, slides, navigation1);
        List<SlideResponse> response2 = mapper.map(renderedDefinition, slides, navigation2);

        verifyDefault(firstPageType, secondPageType);
        assertTrue(navigation1 instanceof Navigation);
        assertTrue(navigation2 instanceof Navigation);
        assertEquals("N1:Content:Content.First", response1.get(0).rootId());
        assertEquals("N1:Content:Content.Second", response1.get(1).rootId());
        assertEquals("N2:Content:Content.First", response2.get(0).rootId());
        assertEquals("N2:Content:Content.Second", response2.get(1).rootId());
        assertEquals(
                List.of("cel_cm_navigation_menuitem", "first", "cel_nav_odd", "cel_nav_item1", "cel_nav_isLeaf",
                        "cel_nav_nodeSpace_Content", "cel_nav_nodeName_First", "FirstPageType", "layout_About-Layout"),
                response1.get(0).cssClasses());
        assertEquals(List.of("cel_cm_navigation_menuitem", "last", "cel_nav_even", "cel_nav_item2",
                "cel_nav_hasChildren", "cel_nav_nodeSpace_Content", "cel_nav_nodeName_Second", "SecondPageType",
                "cel_nav_restricted_rights"), response1.get(1).cssClasses());
        response1.forEach(response -> {
            assertFalse(response.cssClasses().contains("currentPage"));
            assertFalse(response.cssClasses().contains("active"));
        });
        assertEquals("cel_cm_presentation_treenode", response2.get(0).cssClasses().get(0));
        assertEquals(response1.get(0).cssClasses().subList(1, response1.get(0).cssClasses().size()),
                response2.get(0).cssClasses().subList(1, response2.get(0).cssClasses().size()));
        assertEquals("cel_cm_presentation_treenode", response2.get(1).cssClasses().get(0));
        assertEquals(response1.get(1).cssClasses().subList(1, response1.get(1).cssClasses().size()),
                response2.get(1).cssClasses().subList(1, response2.get(1).cssClasses().size()));
    }

}
