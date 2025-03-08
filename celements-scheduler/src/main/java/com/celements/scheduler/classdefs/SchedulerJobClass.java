package com.celements.scheduler.classdefs;

import org.springframework.stereotype.Component;
import org.xwiki.model.reference.ClassReference;

import com.celements.marshalling.EnumMarshaller;
import com.celements.model.classes.AbstractClassDefinition;
import com.celements.model.classes.fields.ClassField;
import com.celements.model.classes.fields.CustomStringField;
import com.celements.model.classes.fields.LargeStringField;
import com.celements.model.classes.fields.StringField;
import com.google.common.base.CaseFormat;
import com.xpn.xwiki.XWikiConstant;

@Component(SchedulerJobClass.CLASS_DEF_HINT)
public class SchedulerJobClass extends AbstractClassDefinition
    implements SchedulerJobClassDefinition {

  public static final String DOC_NAME = "SchedulerJobClass";
  public static final String XWIKI_SPACE = XWikiConstant.XWIKI_SPACE;
  public static final String CLASS_DEF_HINT = XWIKI_SPACE + "." + DOC_NAME;
  public static final ClassReference CLASS_REF = new ClassReference(XWIKI_SPACE, DOC_NAME);

  public enum Status {
    NORMAL, NONE, PAUSED, BLOCKED, COMPLETE, ERROR
  }

  public static final ClassField<String> FIELD_JOB_NAME = new StringField.Builder(
      CLASS_REF, "jobName")
          .prettyName("Job Name")
          .size(60)
          .build();

  public static final ClassField<String> FIELD_JOB_DESCRIPTION = new LargeStringField.Builder(
      CLASS_REF, "jobDescription")
          .rows(10)
          .prettyName("Job Description")
          .size(45)
          .build();

  public static final ClassField<String> FIELD_JOB_CLASS = new StringField.Builder(
      CLASS_REF, "jobClass")
          .prettyName("Job Class")
          .size(60)
          .build();

  public static final ClassField<Status> FIELD_STATUS = new CustomStringField.Builder<>(
      CLASS_REF, "status", new EnumMarshaller<>(
          Status.class, (e -> CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, e.name()))))
              .size(30)
              .prettyName("Status")
              .build();

  public static final ClassField<String> FIELD_CRON = new StringField.Builder(
      CLASS_REF, "cron")
          .prettyName("Cron Expression")
          .size(30)
          .build();

  public static final ClassField<String> FIELD_SCRIPT = new LargeStringField.Builder(
      CLASS_REF, "script")
          .rows(10)
          .prettyName("Job Script")
          .size(60)
          .build();

  public static final ClassField<String> FIELD_CONTEXT_USER = new StringField.Builder(
      CLASS_REF, "contextUser")
          .prettyName("Job execution context user")
          .size(30)
          .build();

  public static final ClassField<String> FIELC_CONTEXT_LANG = new StringField.Builder(
      CLASS_REF, "contextLang")
          .prettyName("Job execution context lang")
          .size(30)
          .build();

  public static final ClassField<String> FIELD_CONTEXT_DATABASE = new StringField.Builder(
      CLASS_REF, "contextDatabase")
          .prettyName("Job execution context database")
          .size(30)
          .build();

  public SchedulerJobClass() {
    super(CLASS_REF);
  }

  @Override
  public boolean isInternalMapping() {
    return false;
  }

}
