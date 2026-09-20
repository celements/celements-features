/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed under the GNU Lesser General Public License, version 2.1 or later.
 */
package com.celements.presentation.rest;

import static org.easymock.EasyMock.createNiceMock;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;

import com.celements.auth.user.UserService;
import com.celements.common.test.AbstractComponentTest;
import com.celements.spring.security.oauth2.IdentityService;

public class PresentationComponentRegistrationTest extends AbstractComponentTest {

    @Override
    protected void beforeSpringContextRefresh(ConfigurableApplicationContext context) {
        super.beforeSpringContextRefresh(context);
        context.addBeanFactoryPostProcessor(beanFactory -> beanFactory.registerSingleton("testIdentityService",
                createNiceMock(IdentityService.class)));
    }

    @Before
    public void prepareTest() {
        ((DefaultListableBeanFactory) getBeanFactory()).registerResolvableDependency(UserService.class,
                createNiceMock(UserService.class));
    }

    @Test
    public void test_controllerAndProductionServiceGraphResolveFromWebContext() {
        assertNotNull(getBeanFactory().getBean(PresentationController.class));
        assertNotNull(getBeanFactory().getBean(PresentationApiService.class));
        assertNotNull(getBeanFactory().getBean(PresentationBatchRenderer.class));
        assertNotNull(getBeanFactory().getBean(PresentationConfigReader.class));
        assertNotNull(getBeanFactory().getBean(PresentationMetadataMapper.class));
        assertNotNull(getBeanFactory().getBean(PresentationRequestResolver.class));
        assertNotNull(getBeanFactory().getBean(PresentationSlideListService.class));
    }

}
