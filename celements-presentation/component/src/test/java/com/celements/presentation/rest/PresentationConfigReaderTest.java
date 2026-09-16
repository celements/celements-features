/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import static java.util.Optional.of;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.xwiki.model.reference.ClassReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.WikiReference;

import com.celements.model.access.IModelAccessFacade;
import com.celements.model.util.ModelUtils;
import com.celements.navigation.INavigationClassConfig;
import com.celements.navigation.NavigationConfig;
import com.celements.navigation.factories.NavigationFactory;
import com.celements.rights.access.EAccessLevel;
import com.celements.rights.access.IRightsAccessFacadeRole;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

public class PresentationConfigReaderTest {

    private IModelAccessFacade modelAccess;
    private IRightsAccessFacadeRole rightsAccess;
    private ModelUtils modelUtils;
    private INavigationClassConfig navigationClassConfig;
    private NavigationFactory<DocumentReference> navigationFactory;
    private PresentationConfigReader reader;
    private DocumentReference configRef;
    private DocumentReference classRef;
    private List<BaseObject> configObjects;
    private SpaceReference explicitMenuSpace;

    @Before
    public void setUpReader() {
        modelAccess = createMock(IModelAccessFacade.class);
        rightsAccess = createMock(IRightsAccessFacadeRole.class);
        modelUtils = createMock(ModelUtils.class);
        navigationClassConfig = createMock(INavigationClassConfig.class);
        navigationFactory = createMock(NavigationFactory.class);
        configObjects = new ArrayList<>();
        reader = new PresentationConfigReader(modelAccess, rightsAccess, modelUtils, navigationClassConfig,
                navigationFactory) {

            @Override
            BaseObject findFirstConfigObject(XWikiDocument configDoc, ClassReference requestedClassRef) {
                return configObjects.stream().findFirst().orElse(null);
            }

            @Override
            SpaceReference resolveMenuSpace(NavigationConfig config, DocumentReference requestedRef) {
                return explicitMenuSpace != null ? explicitMenuSpace : super.resolveMenuSpace(config, requestedRef);
            }
        };
        configRef = new DocumentReference("xwiki", "Config", "WebHome");
        classRef = new DocumentReference("xwiki", "Celements2", "NavigationConfigClass");
    }

    @Test
    public void read_missingAndInaccessibleUseSamePublicError() {
        expect(modelUtils.serializeRefLocal(configRef)).andReturn("Config.WebHome").times(2);
        expect(modelAccess.exists(configRef)).andReturn(false).andReturn(true);
        expect(rightsAccess.hasAccessLevel(configRef, EAccessLevel.VIEW)).andReturn(false);
        replayAll();
        assertUnavailable();
        assertUnavailable();
        verifyAll();
    }

    @Test
    public void read_usesFirstObjectAndDefaultsWhileIgnoringHierarchyAndPagingFields() {
        XWikiDocument doc = configDocument("  renderedContentDynLoad  ", "ignoredLater");
        NavigationConfig config = new NavigationConfig.Builder().menuPart("homepage").layoutType("grid")
                .fromHierarchyLevel(5).toHierarchyLevel(9).showInactiveToLevel(8).nrOfItemsPerPage(2).build();
        expectReadable(doc, config);
        replayAll();
        PresentationDefinition result = reader.read(configRef);
        assertEquals(configRef, result.configRef());
        assertEquals(configRef.getLastSpaceReference(), result.menuSpace());
        assertEquals("homepage", result.menuPart());
        assertEquals("renderedContentDynLoad", result.presentationType());
        assertEquals(PresentationConfigReader.DEFAULT_CSS_CLASS, result.cssClass());
        verifyAll();
    }

    @Test
    public void read_honorsExplicitMenuSpacePartAndCssClass() {
        XWikiDocument doc = configDocument(" ");
        SpaceReference menuSpace = new SpaceReference("Slides", new WikiReference("xwiki"));
        explicitMenuSpace = menuSpace;
        NavigationConfig.Builder builder = new NavigationConfig.Builder().menuPart("stage")
                .dataType(NavigationConfig.PAGE_MENU_DATA_TYPE).cmCssClass(" custom-one custom-two ");
        NavigationConfig config = builder.build();
        expectReadable(doc, config);
        replayAll();
        PresentationDefinition result = reader.read(configRef);
        assertEquals(menuSpace, result.menuSpace());
        assertEquals("stage", result.menuPart());
        assertEquals("default", result.presentationType());
        assertEquals("custom-one custom-two", result.cssClass());
        verifyAll();
    }

    @Test
    public void read_nonPageMenuIsUnavailable() {
        XWikiDocument doc = configDocument("default");
        expectReadable(doc, new NavigationConfig.Builder().dataType("languageMenu").build());
        replayAll();
        assertUnavailable();
        verifyAll();
    }

    @Test
    public void read_objectlessDocumentIsUnavailable() {
        XWikiDocument doc = configDocument();
        expect(modelUtils.serializeRefLocal(configRef)).andReturn("Config.WebHome");
        expect(modelAccess.exists(configRef)).andReturn(true);
        expect(rightsAccess.hasAccessLevel(configRef, EAccessLevel.VIEW)).andReturn(true);
        expect(modelAccess.getDocumentOpt(configRef)).andReturn(of(doc));
        expect(navigationClassConfig.getNavigationConfigClassRef(configRef.getWikiReference())).andReturn(classRef);
        replayAll();
        assertUnavailable();
        verifyAll();
    }

    private XWikiDocument configDocument(String... presentationTypes) {
        XWikiDocument doc = new XWikiDocument(configRef);
        for (String presentationType : presentationTypes) {
            BaseObject object = new BaseObject();
            object.setDocumentReference(configRef);
            object.setXClassReference(classRef);
            object.setStringValue(INavigationClassConfig.PRESENTATION_TYPE_FIELD, presentationType);
            configObjects.add(object);
        }
        return doc;
    }

    private void expectReadable(XWikiDocument doc, NavigationConfig config) {
        expect(modelUtils.serializeRefLocal(configRef)).andReturn("Config.WebHome");
        expect(modelAccess.exists(configRef)).andReturn(true);
        expect(rightsAccess.hasAccessLevel(configRef, EAccessLevel.VIEW)).andReturn(true);
        expect(modelAccess.getDocumentOpt(configRef)).andReturn(of(doc));
        expect(navigationClassConfig.getNavigationConfigClassRef(configRef.getWikiReference())).andReturn(classRef);
        expect(navigationFactory.getNavigationConfig(configRef)).andReturn(config);
    }

    private void assertUnavailable() {
        try {
            reader.read(configRef);
            fail("Expected presentation_not_found");
        } catch (PresentationException exc) {
            assertEquals("presentation_not_found", exc.getCode());
        }
    }

    private void replayAll() {
        replay(modelAccess, rightsAccess, modelUtils, navigationClassConfig, navigationFactory);
    }

    private void verifyAll() {
        verify(modelAccess, rightsAccess, modelUtils, navigationClassConfig, navigationFactory);
    }

}
