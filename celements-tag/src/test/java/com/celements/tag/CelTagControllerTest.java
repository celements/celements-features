package com.celements.tag;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.framework.Advised;
import org.xwiki.model.reference.WikiReference;

import com.celements.auth.user.UserService;
import com.celements.common.test.AbstractComponentTest;
import com.celements.model.context.ModelContext;
import com.celements.model.util.ModelUtils;
import com.celements.tag.controller.CelTagController;
import com.celements.web.service.IWebUtilsService;
import com.google.common.collect.ImmutableMultimap;

import one.util.streamex.StreamEx;

public class CelTagControllerTest extends AbstractComponentTest {

  private CelTagController controller;

  @Before
  public void prepareTest() throws Exception {
    registerComponentMocks(
        CelTagService.class,
        UserService.class,
        ModelContext.class,
        ModelUtils.class,
        IWebUtilsService.class);
    controller = getBeanTarget(CelTagController.class);
  }

  @Test
  public void test_getTagsByType_subWikiFiltersScope() {
    var wiki = new WikiReference("proz");
    var tags = createTags(wiki);
    expectContext(wiki, false);
    expect(getMock(CelTagService.class).streamTags("type"))
        .andReturn(StreamEx.of(tags));
    expectTagDtos(tags);

    replayDefault();
    assertEquals(List.of("global", "local"), controller.getTagsByType("type").stream()
        .map(dto -> dto.name)
        .toList());
    verifyDefault();
  }

  @Test
  public void test_getTagsByType_mainWikiReturnsAllScopes() {
    var wiki = new WikiReference("celements2web");
    var tags = createTags(new WikiReference("proz"));
    expectContext(wiki, true);
    expect(getMock(CelTagService.class).streamTags("type"))
        .andReturn(StreamEx.of(tags));
    expectTagDtos(tags);

    replayDefault();
    assertEquals(List.of("global", "local", "foreign"),
        controller.getTagsByType("type").stream()
            .map(dto -> dto.name)
            .toList());
    verifyDefault();
  }

  private List<CelTag> createTags(WikiReference localWiki) {
    return List.of(
        CelTag.builder().type("type").name("global").build(),
        CelTag.builder().type("type").name("local").scope(localWiki).build(),
        CelTag.builder().type("type").name("foreign")
            .scope(new WikiReference("foreign")).build());
  }

  private void expectContext(WikiReference wiki, boolean mainWiki) {
    expect(getMock(ModelContext.class).getWikiRef()).andReturn(wiki).anyTimes();
    expect(getMock(ModelUtils.class).isMainWiki(wiki)).andReturn(mainWiki).anyTimes();
    expect(getMock(IWebUtilsService.class).getAllowedLanguages()).andReturn(List.of()).anyTimes();
  }

  private void expectTagDtos(List<CelTag> tags) {
    expect(getMock(CelTagService.class).getTagsByType())
        .andReturn(ImmutableMultimap.<String, CelTag>builder()
            .putAll("type", tags)
            .build())
        .anyTimes();
    expect(getMock(ModelUtils.class).serializeRef(anyObject()))
        .andAnswer(() -> ((WikiReference) getCurrentArguments()[0]).getName())
        .anyTimes();
  }

  @SuppressWarnings("unchecked")
  private <T> T getBeanTarget(Class<T> beanClass) throws Exception {
    T bean = getBeanFactory().getBean(beanClass);
    return bean instanceof Advised advised
        ? (T) advised.getTargetSource().getTarget()
        : bean;
  }
}
