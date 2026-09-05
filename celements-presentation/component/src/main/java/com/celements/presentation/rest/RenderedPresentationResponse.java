/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import java.util.List;

public record RenderedPresentationResponse(String language, List<RenderedSlideResponse> slides) {
}
