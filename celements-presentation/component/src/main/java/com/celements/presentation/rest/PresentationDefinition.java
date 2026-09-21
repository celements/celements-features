/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;

record PresentationDefinition(DocumentReference configRef, SpaceReference menuSpace, String menuPart,
        String presentationType, String cssClass) {
}
