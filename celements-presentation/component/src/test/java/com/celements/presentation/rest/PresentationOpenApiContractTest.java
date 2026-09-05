/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import static org.easymock.EasyMock.createNiceMock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springdoc.core.SpringDocConfigProperties;
import org.springdoc.core.SpringDocConfiguration;
import org.springdoc.webmvc.core.SpringDocWebMvcConfiguration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PresentationOpenApiContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;

    @Before
    public void prepareTest() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(DelegatingWebMvcConfiguration.class, SpringDocConfiguration.class,
                SpringDocWebMvcConfiguration.class);
        context.addBeanFactoryPostProcessor(beanFactory -> {
            beanFactory.registerSingleton(SpringDocConfigProperties.class.getName(), new SpringDocConfigProperties());
            beanFactory.registerSingleton(PresentationController.class.getName(),
                    new PresentationController(createNiceMock(PresentationApiService.class)));
        });
        context.refresh();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @After
    public void cleanup() {
        context.close();
    }

    @Test
    public void apiDocsExposeBothPresentationOperations() throws Exception {
        var result = mockMvc.perform(get("/api/v3/api-docs").servletPath("/api")).andExpect(status().isOk())
                .andReturn();
        JsonNode api = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        JsonNode presentation = api.at("/paths/~1v1~1presentations/get");
        JsonNode slides = api.at("/paths/~1v1~1presentations~1slides/get");
        assertFalse(presentation.isMissingNode());
        assertFalse(slides.isMissingNode());
        assertEquals(Set.of("presentationConfigFullName", "language", "navigationNumber"),
                parameterNames(presentation));
        assertEquals(
                Set.of("presentationConfigFullName", "slideFullName", "renderType", "language", "navigationNumber"),
                parameterNames(slides));
        assertScalarParameter(presentation, "presentationConfigFullName", "string", true);
        assertScalarParameter(slides, "presentationConfigFullName", "string", true);
        assertScalarParameter(presentation, "language", "string", false);
        assertScalarParameter(slides, "language", "string", false);
        assertNavigationNumber(presentation);
        assertNavigationNumber(slides);
        JsonNode slideFullName = parameter(slides, "slideFullName");
        assertTrue(slideFullName.get("required").asBoolean());
        assertEquals("array", slideFullName.at("/schema/type").asText());
        assertEquals("string", slideFullName.at("/schema/items/type").asText());
        assertEquals(1, slideFullName.at("/schema/minItems").asInt());
        assertTrue(slideFullName.at("/schema/uniqueItems").asBoolean());
        JsonNode renderType = parameter(slides, "renderType");
        assertTrue(renderType.get("required").asBoolean());
        assertEquals("string", renderType.at("/schema/type").asText());
        assertEquals(2, renderType.at("/schema/enum").size());
        assertEquals("renderedContent", renderType.at("/schema/enum/0").asText());
        assertEquals("renderedExtract", renderType.at("/schema/enum/1").asText());
        assertResponseSchema(api, presentation, "PresentationResponse",
                Set.of("presentationConfigFullName", "presentationType", "language", "slides"));
        assertResponseSchema(api, slides, "RenderedPresentationResponse", Set.of("language", "slides"));
        assertEquals(Set.of("200", "400", "404"), responseCodes(presentation));
        assertEquals(Set.of("200", "400", "404", "500"), responseCodes(slides));
        assertControlledErrorResponse(api, presentation, "400");
        assertControlledErrorResponse(api, presentation, "404");
        assertControlledErrorResponse(api, slides, "400");
        assertControlledErrorResponse(api, slides, "404");
        assertControlledErrorResponse(api, slides, "500");
        assertStringFields(api, "PresentationResponse",
                Set.of("presentationConfigFullName", "presentationType", "language"));
        assertArrayField(api, "PresentationResponse", "slides", "SlideResponse");
        assertArrayField(api, "SlideResponse", "cssClasses", null);
        assertSchemaFields(api, "SlideResponse",
                Set.of("fullName", "docName", "menuLabel", "title", "rootId", "pageLayout", "cssClasses"));
        assertStringFields(api, "SlideResponse",
                Set.of("fullName", "docName", "menuLabel", "title", "rootId", "pageLayout"));
        assertTrue(api.at("/components/schemas/SlideResponse/properties/pageLayout/nullable").asBoolean());
        assertStringFields(api, "RenderedPresentationResponse", Set.of("language"));
        assertArrayField(api, "RenderedPresentationResponse", "slides", "RenderedSlideResponse");
        assertSchemaFields(api, "RenderedSlideResponse", Set.of("fullName", "rootId", "renderedContent"));
        assertStringFields(api, "RenderedSlideResponse", Set.of("fullName", "rootId", "renderedContent"));
    }

    private void assertControlledErrorResponse(JsonNode api, JsonNode operation, String responseCode) {
        assertEquals("#/components/schemas/ErrorResponse",
                operation.at("/responses/" + responseCode + "/content/application~1json/schema/$ref").asText());
        assertSchemaFields(api, "ErrorResponse", Set.of("code"));
        assertStringFields(api, "ErrorResponse", Set.of("code"));
    }

    private void assertScalarParameter(JsonNode operation, String name, String type, boolean required) {
        JsonNode parameter = parameter(operation, name);
        assertEquals(required, parameter.path("required").asBoolean(false));
        assertEquals(type, parameter.at("/schema/type").asText());
        assertFalse(parameter.at("/schema/items").isObject());
    }

    private void assertNavigationNumber(JsonNode operation) {
        JsonNode parameter = parameter(operation, "navigationNumber");
        assertFalse(parameter.path("required").asBoolean(false));
        assertEquals("integer", parameter.at("/schema/type").asText());
        assertEquals("1", parameter.at("/schema/default").asText());
        assertEquals("1", parameter.at("/schema/minimum").asText());
    }

    private void assertResponseSchema(JsonNode api, JsonNode operation, String schemaName, Set<String> fields) {
        assertEquals("#/components/schemas/" + schemaName,
                operation.at("/responses/200/content/application~1json/schema/$ref").asText());
        assertSchemaFields(api, schemaName, fields);
    }

    private void assertSchemaFields(JsonNode api, String schemaName, Set<String> fields) {
        JsonNode properties = api.at("/components/schemas/" + schemaName + "/properties");
        assertTrue(properties.isObject());
        Set<String> actualFields = new HashSet<>();
        properties.fieldNames().forEachRemaining(actualFields::add);
        assertEquals(fields, actualFields);
    }

    private void assertArrayField(JsonNode api, String schemaName, String fieldName, String itemSchemaName) {
        JsonNode schema = api.at("/components/schemas/" + schemaName + "/properties/" + fieldName);
        assertEquals("array", schema.get("type").asText());
        if (itemSchemaName == null) {
            assertEquals("string", schema.at("/items/type").asText());
        } else {
            assertEquals("#/components/schemas/" + itemSchemaName, schema.at("/items/$ref").asText());
        }
    }

    private void assertStringFields(JsonNode api, String schemaName, Set<String> fieldNames) {
        for (String fieldName : fieldNames) {
            assertEquals("string",
                    api.at("/components/schemas/" + schemaName + "/properties/" + fieldName + "/type").asText());
        }
    }

    private JsonNode parameter(JsonNode operation, String name) {
        for (JsonNode parameter : operation.withArray("parameters")) {
            if (name.equals(parameter.path("name").asText())) {
                return parameter;
            }
        }
        throw new AssertionError("Missing parameter " + name);
    }

    private Set<String> parameterNames(JsonNode operation) {
        Set<String> names = new HashSet<>();
        operation.get("parameters").forEach(parameter -> names.add(parameter.get("name").asText()));
        return names;
    }

    private Set<String> responseCodes(JsonNode operation) {
        Set<String> responseCodes = new HashSet<>();
        operation.get("responses").fieldNames().forEachRemaining(responseCodes::add);
        return responseCodes;
    }

}
