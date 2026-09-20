/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import javax.inject.Inject;
import javax.inject.Named;

import org.springframework.stereotype.Component;
import org.xwiki.model.reference.ClassReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;

import com.celements.model.access.IModelAccessFacade;
import com.celements.model.object.xwiki.XWikiObjectFetcher;
import com.celements.model.util.ModelUtils;
import com.celements.navigation.INavigationClassConfig;
import com.celements.navigation.NavigationConfig;
import com.celements.navigation.factories.NavigationFactory;
import com.celements.navigation.factories.XObjectNavigationFactory;
import com.celements.rights.access.EAccessLevel;
import com.celements.rights.access.IRightsAccessFacadeRole;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

@Component
public class PresentationConfigReader {

    private final IModelAccessFacade modelAccess;
    private final IRightsAccessFacadeRole rightsAccess;
    private final ModelUtils modelUtils;
    private final INavigationClassConfig navigationClassConfig;
    private final NavigationFactory<DocumentReference> navigationFactory;

    @Inject
    public PresentationConfigReader(IModelAccessFacade modelAccess, IRightsAccessFacadeRole rightsAccess,
            ModelUtils modelUtils, INavigationClassConfig navigationClassConfig,
            @Named(XObjectNavigationFactory.XOBJECT_NAV_FACTORY_HINT) NavigationFactory<DocumentReference> navigationFactory) {
        this.modelAccess = modelAccess;
        this.rightsAccess = rightsAccess;
        this.modelUtils = modelUtils;
        this.navigationClassConfig = navigationClassConfig;
        this.navigationFactory = navigationFactory;
    }

    public PresentationDefinition read(DocumentReference configRef) {
        String configName = modelUtils.serializeRefLocal(configRef);
        if (!modelAccess.exists(configRef) || !rightsAccess.hasAccessLevel(configRef, EAccessLevel.VIEW)) {
            throw unavailable(configName, "document missing or not viewable");
        }
        XWikiDocument configDoc = modelAccess.getDocumentOpt(configRef)
                .orElseThrow(() -> unavailable(configName, "document unavailable after existence check"));
        BaseObject configObject = findFirstConfigObject(configDoc,
                new ClassReference(navigationClassConfig.getNavigationConfigClassRef(configRef.getWikiReference())));
        if (configObject == null) {
            throw unavailable(configName, "first navigation config object missing");
        }
        NavigationConfig config = navigationFactory.getNavigationConfig(configRef);
        if (!NavigationConfig.PAGE_MENU_DATA_TYPE.equals(config.getDataType())) {
            throw unavailable(configName, "navigation config is not a page menu");
        }
        SpaceReference menuSpace = resolveMenuSpace(config, configRef);
        String configuredType = configObject.getStringValue(INavigationClassConfig.PRESENTATION_TYPE_FIELD).trim();
        String presentationType = configuredType.isEmpty() ? "default" : configuredType;
        String configuredCssClass = config.getCssClass().trim();
        return new PresentationDefinition(configRef, menuSpace, config.getMenuPart(), presentationType,
                configuredCssClass);
    }

    private PresentationException unavailable(String configName, String reason) {
        return PresentationException.notFound("presentation_not_found",
                "Presentation " + configName + " unavailable: " + reason);
    }

    BaseObject findFirstConfigObject(XWikiDocument configDoc, ClassReference classRef) {
        return XWikiObjectFetcher.on(configDoc).filter(classRef).stream().findFirst().orElse(null);
    }

    SpaceReference resolveMenuSpace(NavigationConfig config, DocumentReference configRef) {
        return config.getNodeSpaceRef().or(configRef.getLastSpaceReference());
    }

}
