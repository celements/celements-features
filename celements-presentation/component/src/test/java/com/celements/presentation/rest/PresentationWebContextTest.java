/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.celements.auth.user.UserService;
import com.celements.common.test.AbstractComponentTest;
import com.celements.spring.security.oauth2.IdentityService;

public class PresentationWebContextTest extends AbstractComponentTest {

    private PresentationApiService service;
    private PresentationController controller;
    private MockMvc mockMvc;

    @Override
    protected void beforeSpringContextRefresh(ConfigurableApplicationContext context) {
        super.beforeSpringContextRefresh(context);
        context.addBeanFactoryPostProcessor(beanFactory -> beanFactory.registerSingleton("testIdentityService",
                createNiceMock(IdentityService.class)));
    }

    @Before
    public void prepareTest() {
        service = createDefaultMock(PresentationApiService.class);
        var beanFactory = (DefaultListableBeanFactory) getBeanFactory();
        beanFactory.destroySingleton(PresentationController.class.getName());
        beanFactory.registerResolvableDependency(PresentationApiService.class, service);
        beanFactory.registerResolvableDependency(UserService.class, createNiceMock(UserService.class));
        controller = beanFactory.getBean(PresentationController.class.getName(), PresentationController.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(getSpringContext()).build();
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("test-key",
                "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
    }

    @After
    public void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void test_discoveredControllerAllowsAnonymousAccessToBothOperations() throws Exception {
        expect(service.getPresentation(List.of("Content.WebHome"), 1)).andReturn(null);
        expect(service.renderSlides(List.of("Content.WebHome"), List.of("Content.First"), List.of("renderedContent"),
                1)).andReturn(null);
        replayDefault();

        assertTrue(AopUtils.isAopProxy(controller));
        assertTrue(SecurityContextHolder.getContext().getAuthentication() instanceof AnonymousAuthenticationToken);
        mockMvc.perform(get("/v1/presentations").param("presentationConfigFullName", "Content.WebHome"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/presentations/slides").param("presentationConfigFullName", "Content.WebHome")
                .param("slideFullName", "Content.First").param("renderType", "renderedContent"))
                .andExpect(status().isOk());

        verifyDefault();
    }

}
