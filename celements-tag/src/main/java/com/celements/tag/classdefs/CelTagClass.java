package com.celements.tag.classdefs;

import java.util.List;

import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.ClassReference;

import com.celements.model.classes.AbstractClassDefinition;
import com.celements.model.classes.fields.ClassField;
import com.celements.model.classes.fields.list.StringListField;
import com.celements.web.classes.CelementsClassDefinition;
import com.google.errorprone.annotations.Immutable;

@Singleton
@Immutable
@Component(CelTagClass.CLASS_DEF_HINT)
public class CelTagClass extends AbstractClassDefinition implements CelementsClassDefinition {
  // TODO define own class package

  public static final String DOC_NAME = "CelTagClass";
  public static final String CLASS_DEF_HINT = CelementsClassDefinition.SPACE_NAME + "." + DOC_NAME;
  public static final ClassReference CLASS_REF = new ClassReference(SPACE_NAME, DOC_NAME);

  public static final ClassField<List<String>> FIELD_TAGS = new StringListField.Builder<>(
      CLASS_REF, "tags")
          .multiSelect(true)
          .build();

  public CelTagClass() {
    super(CLASS_REF);
  }

  @Override
  public boolean isInternalMapping() {
    return true;
  }

}