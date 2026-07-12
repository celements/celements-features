package com.celements.tag;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.http.HttpStatus;
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
    assertEquals(List.of("local", "global"), controller.getTagsByType("type").stream()
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
    assertEquals(List.of("local", "foreign", "global"),
        controller.getTagsByType("type").stream()
            .map(dto -> dto.name)
            .toList());
    verifyDefault();
  }

  @Test
  public void test_getTags_subWikiFiltersScopeAndGroupsRoots() {
    var wiki = new WikiReference("proz");
    var tags = createTags(wiki);
    var localChild = createChild(tags.get(0), "child", 1, wiki);
    var otherType = CelTag.builder().type("other").name("other").scope(wiki).build();
    tags = StreamEx.of(tags).append(localChild, otherType).toList();
    expectContext(wiki, false);
    expect(getMock(CelTagService.class).streamAllTags()).andReturn(StreamEx.of(tags));
    expectTagDtos(tags);

    replayDefault();
    var result = controller.getTags();
    assertEquals(List.of("global", "local"), result.get("type").stream()
        .map(dto -> dto.name)
        .toList());
    assertEquals(List.of("other"), result.get("other").stream()
        .map(dto -> dto.name)
        .toList());
    assertEquals(List.of("child"), result.get("type").get(0).children.stream()
        .map(dto -> dto.name)
        .toList());
    verifyDefault();
  }

  @Test
  public void test_getTypes_subWikiOnlyReturnsVisibleTypes() {
    var wiki = new WikiReference("proz");
    var tags = List.of(
        CelTag.builder().type("globalType").name("global").build(),
        CelTag.builder().type("localType").name("local").scope(wiki).build(),
        CelTag.builder().type("foreignType").name("foreign")
            .scope(new WikiReference("foreign")).build());
    expectContext(wiki, false);
    expect(getMock(CelTagService.class).streamAllTags()).andReturn(StreamEx.of(tags));

    replayDefault();
    assertEquals(Set.of("globaltype", "localtype"), controller.getTypes());
    verifyDefault();
  }

  @Test
  public void test_getTagByName_subWikiSelectsMatchingScope() {
    var wiki = new WikiReference("proz");
    var foreign = CelTag.builder().type("type").name("shared")
        .scope(new WikiReference("foreign")).build();
    var local = CelTag.builder().type("type").name("shared").scope(wiki).build();
    var tags = List.of(foreign, local);
    expectContext(wiki, false);
    expect(getMock(CelTagService.class).streamTags("type"))
        .andReturn(StreamEx.of(tags));
    expectTagDtos(tags);

    replayDefault();
    var response = controller.getTagByName("type", "shared");
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("proz", response.getBody().scope);
    verifyDefault();
  }

  @Test
  public void test_getTagByName_notFound() {
    var wiki = new WikiReference("proz");
    var foreign = CelTag.builder().type("type").name("shared")
        .scope(new WikiReference("foreign")).build();
    expectContext(wiki, false);
    expect(getMock(CelTagService.class).streamTags("type"))
        .andReturn(StreamEx.of(foreign));

    replayDefault();
    assertEquals(HttpStatus.NOT_FOUND,
        controller.getTagByName("type", "shared").getStatusCode());
    verifyDefault();
  }

  @Test
  public void test_tagDto_subWikiSerializesFieldsAndFiltersChildren() {
    var wiki = new WikiReference("proz");
    var root = CelTag.builder().type("type").name("root").order(3)
        .prettyName(lang -> "de".equals(lang) ? Optional.of("Wurzel") : Optional.empty())
        .build();
    var localChild = createChild(root, "local", 2, wiki);
    var foreignChild = createChild(root, "foreign", 1, new WikiReference("foreign"));
    var tags = List.of(root, localChild, foreignChild);
    expectContext(wiki, false);
    expectTagDtos(tags, List.of("de", "en"));

    replayDefault();
    var dto = controller.new TagDto(root);
    assertEquals("root", dto.name);
    assertNull(dto.scope);
    assertEquals(3, dto.order);
    assertEquals("Wurzel", dto.prettyName.get("de"));
    assertFalse(dto.prettyName.containsKey("en"));
    assertEquals(List.of("local"), dto.children.stream().map(child -> child.name).toList());
    assertEquals("proz", dto.children.get(0).scope);
    verifyDefault();
  }

  private List<CelTag> createTags(WikiReference localWiki) {
    return List.of(
        CelTag.builder().type("type").name("global").order(3).build(),
        CelTag.builder().type("type").name("local").order(1).scope(localWiki).build(),
        CelTag.builder().type("type").name("foreign")
            .order(2).scope(new WikiReference("foreign")).build());
  }

  private CelTag createChild(CelTag parent, String name, int order, WikiReference scope) {
    var builder = CelTag.builder().type(parent.getType()).name(name).parent(parent.getName())
        .order(order).scope(scope);
    builder.addDependency(parent);
    return builder.build();
  }

  private void expectContext(WikiReference wiki, boolean mainWiki) {
    expect(getMock(ModelContext.class).getWikiRef()).andReturn(wiki).anyTimes();
    expect(getMock(ModelUtils.class).isMainWiki(wiki)).andReturn(mainWiki).anyTimes();
  }

  private void expectTagDtos(List<CelTag> tags) {
    expectTagDtos(tags, List.of());
  }

  private void expectTagDtos(List<CelTag> tags, List<String> languages) {
    expect(getMock(IWebUtilsService.class).getAllowedLanguages())
        .andReturn(languages).anyTimes();
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
