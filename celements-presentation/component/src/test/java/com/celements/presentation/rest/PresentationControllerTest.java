/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class PresentationControllerTest {

    private PresentationApiService service;
    private PresentationController controller;
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        service = createMock(PresentationApiService.class);
        controller = new PresentationController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void getPresentation_setsPrivateResponseHeaders() {
        PresentationResponse body = new PresentationResponse("Content.WebHome", "default", "de", List.of());
        expect(service.getPresentation(List.of("Content.WebHome"), 1)).andReturn(body);
        replay(service);
        ResponseEntity<PresentationResponse> response = controller.getPresentation(List.of("Content.WebHome"), "de", 1);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("application/json;charset=UTF-8", response.getHeaders().getContentType().toString());
        assertEquals("private, no-cache", response.getHeaders().getCacheControl());
        assertEquals(List.of("Cookie", "Accept-Language"), response.getHeaders().getVary());
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals(body, response.getBody());
        verify(service);
    }

    @Test
    public void handlePresentationException_returnsCodeOnlyAndNoStore() {
        ResponseEntity<PresentationController.ErrorResponse> response = controller
                .handlePresentationException(PresentationException.badRequest("invalid_render_type"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("private, no-store", response.getHeaders().getCacheControl());
        assertEquals(new PresentationController.ErrorResponse("invalid_render_type"), response.getBody());
    }

    @Test
    public void logPresentationException_controlled4xxUsesInfoWithoutThrowable() {
        Logger logger = createMock(Logger.class);
        RuntimeException cause = new RuntimeException("invalid anonymous input");
        PresentationException exc = PresentationException.badRequest("invalid_slide_selection",
                "Rejected slideFullName [bad]", cause);
        logger.info("Controlled presentation REST failure [{}]: {}", "invalid_slide_selection",
                "Rejected slideFullName [bad]");
        expectLastCall();
        replay(logger);
        PresentationController.logPresentationException(logger, exc);
        verify(logger);
    }

    @Test
    public void logPresentationException_controlled500UsesErrorWithCause() {
        Logger logger = createMock(Logger.class);
        RuntimeException cause = new RuntimeException("renderer failure");
        PresentationException exc = PresentationException
                .renderingFailed("Failed presentation Content.WebHome slide Content.First", cause);
        logger.error("Controlled presentation REST failure [{}]: {}", "rendering_failed",
                "Failed presentation Content.WebHome slide Content.First", cause);
        expectLastCall();
        replay(logger);
        PresentationController.logPresentationException(logger, exc);
        verify(logger);
    }

    @Test
    public void getPresentation_httpContractHasHeadersAndNoCors() throws Exception {
        PresentationResponse body = new PresentationResponse("Content.WebHome", "default", "de", List.of());
        expect(service.getPresentation(List.of("Content.WebHome"), 1)).andReturn(body);
        replay(service);
        mockMvc.perform(get("/v1/presentations").param("presentationConfigFullName", "Content.WebHome")
                .param("language", "de").accept("application/json")).andExpect(status().isOk())
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "private, no-cache"))
                .andExpect(header().string("Vary", "Cookie, Accept-Language"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verify(service);
    }

    @Test
    public void getPresentation_missingRequiredParameterIsStandardBadRequest() throws Exception {
        replay(service);
        mockMvc.perform(get("/v1/presentations").accept("application/json")).andExpect(status().isBadRequest());
        verify(service);
    }

    @Test
    public void getPresentation_incompatibleAcceptIsStandardNotAcceptable() throws Exception {
        replay(service);
        mockMvc.perform(get("/v1/presentations").param("presentationConfigFullName", "Content.WebHome")
                .accept("application/xml")).andExpect(status().isNotAcceptable());
        verify(service);
    }

    @Test
    public void getPresentation_controlledErrorContainsOnlyCode() throws Exception {
        expect(service.getPresentation(List.of("bad"), 1))
                .andThrow(PresentationException.badRequest("invalid_presentation_reference"));
        replay(service);
        mockMvc.perform(get("/v1/presentations").param("presentationConfigFullName", "bad").accept("application/json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("{\"code\":\"invalid_presentation_reference\"}"))
                .andExpect(header().string("Cache-Control", "private, no-store"));
        verify(service);
    }

    @Test
    public void renderSlides_httpSuccessPreservesArrayShapeAndHeaders() throws Exception {
        RenderedPresentationResponse body = new RenderedPresentationResponse("de",
                List.of(new RenderedSlideResponse("Content.Second", "N2:Content:Content.Second", "<p>two</p>"),
                        new RenderedSlideResponse("Content.First", "N2:Content:Content.First", "<p>one</p>")));
        expect(service.renderSlides(List.of("Content.WebHome"), List.of("Content.Second", "Content.First"),
                List.of("renderedContent"), 2)).andReturn(body);
        replay(service);
        mockMvc.perform(get("/v1/presentations/slides").param("presentationConfigFullName", "Content.WebHome")
                .param("slideFullName", "Content.Second", "Content.First").param("renderType", "renderedContent")
                .param("language", "de").param("navigationNumber", "2").accept("application/json"))
                .andExpect(status().isOk()).andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(content().string("{\"language\":\"de\",\"slides\":["
                        + "{\"fullName\":\"Content.Second\",\"rootId\":\"N2:Content:Content.Second\","
                        + "\"renderedContent\":\"<p>two</p>\"},"
                        + "{\"fullName\":\"Content.First\",\"rootId\":\"N2:Content:Content.First\","
                        + "\"renderedContent\":\"<p>one</p>\"}]}"))
                .andExpect(header().string("Cache-Control", "private, no-cache"))
                .andExpect(header().string("Vary", "Cookie, Accept-Language"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verify(service);
    }

    @Test
    public void renderSlides_controlledErrorHasCodeOnlyAndNoStoreHeaders() throws Exception {
        expect(service.renderSlides(List.of("Content.WebHome"), List.of("Content.Missing"), List.of("renderedExtract"),
                1)).andThrow(PresentationException.notFound("slide_not_found", "internal member diagnostic"));
        replay(service);
        mockMvc.perform(get("/v1/presentations/slides").param("presentationConfigFullName", "Content.WebHome")
                .param("slideFullName", "Content.Missing").param("renderType", "renderedExtract")
                .accept("application/json")).andExpect(status().isNotFound())
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(content().string("{\"code\":\"slide_not_found\"}"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Vary", "Cookie, Accept-Language"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        verify(service);
    }

    @Test
    public void renderSlides_malformedNavigationNumberIsStandardBadRequest() throws Exception {
        replay(service);
        mockMvc.perform(get("/v1/presentations/slides").param("presentationConfigFullName", "Content.WebHome")
                .param("slideFullName", "Content.First").param("renderType", "renderedContent")
                .param("navigationNumber", "not-an-integer").accept("application/json"))
                .andExpect(status().isBadRequest());
        verify(service);
    }

    @Test
    public void getPresentation_presentationNotFoundUsesControlledErrorContract() throws Exception {
        expect(service.getPresentation(List.of("Content.Missing"), 1)).andThrow(
                PresentationException.notFound("presentation_not_found", "safe internal configuration detail"));
        replay(service);
        mockMvc.perform(get("/v1/presentations").param("presentationConfigFullName", "Content.Missing")
                .accept("application/json")).andExpect(status().isNotFound())
                .andExpect(content().string("{\"code\":\"presentation_not_found\"}"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Vary", "Cookie, Accept-Language"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        verify(service);
    }

    @Test
    public void renderSlides_remainingControlledErrorsUseCodeOnlyMatrix() throws Exception {
        expect(service.renderSlides(List.of("Content.WebHome"), List.of("bad"), List.of("renderedContent"), 1))
                .andThrow(PresentationException.badRequest("invalid_slide_selection", "Rejected slideFullName [bad]",
                        null));
        expect(service.renderSlides(List.of("Content.WebHome"), List.of("Content.First"), List.of("bad"), 1)).andThrow(
                PresentationException.badRequest("invalid_render_type", "Unsupported renderType [bad]", null));
        expect(service
                .renderSlides(List.of("Content.WebHome"), List.of("Content.First"), List.of("renderedContent"), 0))
                        .andThrow(PresentationException.badRequest("invalid_navigation_number",
                                "navigationNumber must be positive but was 0", null));
        RuntimeException cause = new RuntimeException("safe renderer failure");
        expect(service
                .renderSlides(List.of("Content.WebHome"), List.of("Content.First"), List.of("renderedContent"), 1))
                        .andThrow(PresentationException
                                .renderingFailed("Failed presentation Content.WebHome slide Content.First", cause));
        replay(service);
        assertSlideError("bad", "renderedContent", "1", 400, "invalid_slide_selection");
        assertSlideError("Content.First", "bad", "1", 400, "invalid_render_type");
        assertSlideError("Content.First", "renderedContent", "0", 400, "invalid_navigation_number");
        assertSlideError("Content.First", "renderedContent", "1", 500, "rendering_failed");
        verify(service);
    }

    private void assertSlideError(String slideName, String renderType, String navigationNumber, int expectedStatus,
            String expectedCode) throws Exception {
        mockMvc.perform(get("/v1/presentations/slides").param("presentationConfigFullName", "Content.WebHome")
                .param("slideFullName", slideName).param("renderType", renderType)
                .param("navigationNumber", navigationNumber).accept("application/json"))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentType("application/json;charset=UTF-8"))
                .andExpect(content().string("{\"code\":\"" + expectedCode + "\"}"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Vary", "Cookie, Accept-Language"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

}
