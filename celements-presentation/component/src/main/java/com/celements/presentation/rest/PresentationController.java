/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.List;

import javax.inject.Inject;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.celements.spring.security.AuthenticatedBaseController;

@RestController
@RequestMapping("/v1/presentations")
@PreAuthorize("permitAll()")
public class PresentationController extends AuthenticatedBaseController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PresentationController.class);
    private static final MediaType JSON_UTF_8 = new MediaType("application", "json", UTF_8);

    private final PresentationApiService service;

    @Inject
    public PresentationController(PresentationApiService service) {
        this.service = service;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = PresentationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Controlled invalid_presentation_reference or "
                    + "invalid_navigation_number error. Framework-generated 400 responses do not "
                    + "guarantee this schema.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Controlled presentation_not_found error.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
    public ResponseEntity<PresentationResponse> getPresentation(
            @Parameter(required = true, schema = @Schema(type = "string")) @RequestParam("presentationConfigFullName") List<String> configNames,
            @RequestParam(name = "language", required = false) String language,
            @Parameter(schema = @Schema(type = "integer", defaultValue = "1", minimum = "1")) @RequestParam(name = "navigationNumber", defaultValue = "1") int navigationNumber) {
        return success(service.getPresentation(configNames, navigationNumber));
    }

    @GetMapping(value = "/slides", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = RenderedPresentationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Controlled invalid_presentation_reference, invalid_slide_selection, "
                    + "invalid_render_type, or invalid_navigation_number error. Framework-generated "
                    + "400 responses do not guarantee this schema.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Controlled presentation_not_found or slide_not_found error.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Controlled rendering_failed error.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))) })
    public ResponseEntity<RenderedPresentationResponse> renderSlides(
            @Parameter(required = true, schema = @Schema(type = "string")) @RequestParam("presentationConfigFullName") List<String> configNames,
            @Parameter(required = true, array = @ArraySchema(schema = @Schema(type = "string"), minItems = 1, uniqueItems = true)) @RequestParam("slideFullName") List<String> slideNames,
            @Parameter(required = true, schema = @Schema(type = "string", allowableValues = { "renderedContent",
                    "renderedExtract" })) @RequestParam("renderType") List<String> renderTypes,
            @RequestParam(name = "language", required = false) String language,
            @Parameter(schema = @Schema(type = "integer", defaultValue = "1", minimum = "1")) @RequestParam(name = "navigationNumber", defaultValue = "1") int navigationNumber) {
        return success(service.renderSlides(configNames, slideNames, renderTypes, navigationNumber));
    }

    @ExceptionHandler(PresentationException.class)
    public ResponseEntity<ErrorResponse> handlePresentationException(PresentationException exc) {
        logPresentationException(LOGGER, exc);
        HttpHeaders headers = responseHeaders("private, no-store");
        return new ResponseEntity<>(new ErrorResponse(exc.getCode()), headers, exc.getStatus());
    }

    static void logPresentationException(Logger logger, PresentationException exc) {
        if (exc.getStatus().is5xxServerError()) {
            logger.error("Controlled presentation REST failure [{}]: {}", exc.getCode(), exc.getDiagnosticContext(),
                    exc.getCause());
        } else {
            logger.info("Controlled presentation REST failure [{}]: {}", exc.getCode(), exc.getDiagnosticContext());
        }
    }

    private <T> ResponseEntity<T> success(T body) {
        return new ResponseEntity<>(body, responseHeaders("private, no-cache"), HttpStatus.OK);
    }

    private HttpHeaders responseHeaders(String cacheControl) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(JSON_UTF_8);
        headers.set(HttpHeaders.CACHE_CONTROL, cacheControl);
        headers.set(HttpHeaders.VARY, "Cookie, Accept-Language");
        headers.set("X-Content-Type-Options", "nosniff");
        return headers;
    }

    public record ErrorResponse(String code) {
    }

}
