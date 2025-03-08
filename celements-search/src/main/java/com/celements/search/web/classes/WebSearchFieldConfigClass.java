package com.celements.search.web.classes;

import static com.google.common.collect.Lists.*;

import java.util.Arrays;
import java.util.List;

import javax.annotation.concurrent.Immutable;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.ClassReference;

import com.celements.model.classes.AbstractClassDefinition;
import com.celements.model.classes.fields.ClassField;
import com.celements.model.classes.fields.StringField;
import com.celements.model.classes.fields.list.EnumListField;
import com.celements.model.classes.fields.list.single.EnumSingleListField;
import com.celements.model.classes.fields.number.FloatField;
import com.celements.search.lucene.query.QueryRestriction.QueryMode;
import com.celements.search.lucene.query.QueryRestrictionGroup.Type;

@Immutable
@Singleton
@Component(WebSearchFieldConfigClass.CLASS_DEF_HINT)
public class WebSearchFieldConfigClass extends AbstractClassDefinition implements
    WebSearchClassDefinition {

  public static final String SPACE_NAME = "Celements2";
  public static final String DOC_NAME = "WebSearchFieldConfigClass";
  public static final String CLASS_DEF_HINT = SPACE_NAME + "." + DOC_NAME;
  public static final ClassReference CLASS_REF = new ClassReference(SPACE_NAME, DOC_NAME);

  public static final ClassField<String> FIELD_NAME = new StringField.Builder(
      CLASS_REF, "fieldName").build();

  public static final ClassField<Type> FIELD_OPERATOR = new EnumSingleListField.Builder<>(
      CLASS_REF, "operator", Type.class).prettyName("Operator (default: OR)")
          .values(reverse(Arrays.asList(Type.values())))
          .build();

  public static final ClassField<List<QueryMode>> FIELD_SEARCH_MODE = new EnumListField.Builder<>(
      CLASS_REF, "searchMode", QueryMode.class).prettyName("Search Mode (default: all)")
          .multiSelect(true).build();

  public static final ClassField<Float> FIELD_BOOST = new FloatField.Builder(
      CLASS_REF, "boost").prettyName("Boost (default: 1.0)").build();

  public static final ClassField<String> FIELD_VALUE = new StringField.Builder(
      CLASS_REF, "value").prettyName("Value (Velocity, optional)").build();

  public WebSearchFieldConfigClass() {
    super(CLASS_REF);
  }

  @Override
  public boolean isInternalMapping() {
    return false;
  }

}
