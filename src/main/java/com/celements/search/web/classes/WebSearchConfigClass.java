package com.celements.search.web.classes;

import java.util.List;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;

import com.celements.marshalling.PageTypeMarshaller;
import com.celements.marshalling.ReferenceMarshaller;
import com.celements.model.classes.AbstractClassDefinition;
import com.celements.model.classes.fields.BooleanField;
import com.celements.model.classes.fields.ClassField;
import com.celements.model.classes.fields.StringField;
import com.celements.model.classes.fields.list.ComponentListField;
import com.celements.model.classes.fields.list.CustomListField;
import com.celements.model.classes.fields.list.StringListField;
import com.celements.model.classes.fields.number.FloatField;
import com.celements.pagetype.PageTypeReference;
import com.celements.search.web.packages.WebSearchPackage;

@Component(WebSearchConfigClass.CLASS_DEF_HINT)
public class WebSearchConfigClass extends AbstractClassDefinition {

  public static final String SPACE_NAME = "Celements2";
  public static final String DOC_NAME = "WebSearchConfigClass";
  public static final String CLASS_DEF_HINT = SPACE_NAME + "." + DOC_NAME;

  public ClassField<List<WebSearchPackage>> FIELD_PACKAGES = new ComponentListField.Builder<>(
      CLASS_DEF_HINT, "packages", WebSearchPackage.class).build();

  public ClassField<Boolean> FIELD_LINKED_DOCS_ONLY = new BooleanField.Builder(CLASS_DEF_HINT,
      "linkedDocsOnly").displayType("yesno").build();

  public ClassField<Float> FIELD_FUZZY_SEARCH = new FloatField.Builder(CLASS_DEF_HINT,
      "fuzzySearch").build();

  public ClassField<List<DocumentReference>> FIELD_DOCS = new CustomListField.Builder<>(
      CLASS_DEF_HINT, "docs", new ReferenceMarshaller<>(DocumentReference.class)).build();

  public ClassField<List<DocumentReference>> FIELD_DOCS_BLACK_LIST = new CustomListField.Builder<>(
      CLASS_DEF_HINT, "docsBlackList", new ReferenceMarshaller<>(DocumentReference.class)).build();

  public ClassField<List<DocumentReference>> FIELD_SPACES = new CustomListField.Builder<>(
      CLASS_DEF_HINT, "spaces", new ReferenceMarshaller<>(DocumentReference.class)).build();

  public ClassField<List<SpaceReference>> FIELD_SPACES_BLACK_LIST = new CustomListField.Builder<>(
      CLASS_DEF_HINT, "spacesBlackList", new ReferenceMarshaller<>(SpaceReference.class)).build();

  public ClassField<List<PageTypeReference>> FIELD_PAGETYPES = new CustomListField.Builder<>(
      CLASS_DEF_HINT, "pageTypes", new PageTypeMarshaller()).build();

  public ClassField<List<PageTypeReference>> FIELD_PAGETYPES_BLACK_LIST = new CustomListField.Builder<>(
      CLASS_DEF_HINT, "pageTypesBlackList", new PageTypeMarshaller()).build();

  public ClassField<List<String>> FIELD_SORT_FIELDS = new StringListField.Builder(CLASS_DEF_HINT,
      "sortFields").build();

  public ClassField<String> FIELD_RESULT_ITEM_RENDER_SCRIPT = new StringField.Builder(
      CLASS_DEF_HINT, "resultItemRenderScript").build();

  @Override
  public String getName() {
    return CLASS_DEF_HINT;
  }

  @Override
  public boolean isInternalMapping() {
    return false;
  }

  @Override
  protected String getClassSpaceName() {
    return SPACE_NAME;
  }

  @Override
  protected String getClassDocName() {
    return DOC_NAME;
  }

}
