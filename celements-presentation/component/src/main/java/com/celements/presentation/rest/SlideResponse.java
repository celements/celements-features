/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record SlideResponse(String fullName, String docName, String menuLabel, String title, String rootId,
        @Schema(nullable = true) String pageLayout, List<String> cssClasses) {
}
