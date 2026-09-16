/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import org.springframework.http.HttpStatus;

public final class PresentationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;
    private final String diagnosticContext;

    private PresentationException(HttpStatus status, String code, String diagnosticContext, Throwable cause) {
        super(diagnosticContext, cause);
        this.status = status;
        this.code = code;
        this.diagnosticContext = diagnosticContext;
    }

    public static PresentationException badRequest(String code) {
        return badRequest(code, code, null);
    }

    public static PresentationException badRequest(String code, String diagnosticContext, Throwable cause) {
        return new PresentationException(HttpStatus.BAD_REQUEST, code, diagnosticContext, cause);
    }

    public static PresentationException notFound(String code) {
        return notFound(code, code);
    }

    public static PresentationException notFound(String code, String diagnosticContext) {
        return new PresentationException(HttpStatus.NOT_FOUND, code, diagnosticContext, null);
    }

    public static PresentationException renderingFailed(String diagnosticContext, Throwable cause) {
        return new PresentationException(HttpStatus.INTERNAL_SERVER_ERROR, "rendering_failed", diagnosticContext,
                cause);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getDiagnosticContext() {
        return diagnosticContext;
    }

}
