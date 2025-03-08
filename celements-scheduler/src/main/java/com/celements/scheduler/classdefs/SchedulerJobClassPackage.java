package com.celements.scheduler.classdefs;

import java.util.List;

import javax.inject.Inject;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.stereotype.Component;

import com.celements.model.classes.AbstractLegacyClassPackage;
import com.celements.model.classes.ClassDefinition;

@Component
public class SchedulerJobClassPackage extends AbstractLegacyClassPackage {

  public static final String NAME = "scheduler";

  private final List<SchedulerJobClassDefinition> classDefs;

  @Inject
  public SchedulerJobClassPackage(ListableBeanFactory beanFactory) {
    this.classDefs = List
        .copyOf(beanFactory.getBeansOfType(SchedulerJobClassDefinition.class).values());
  }

  @Override
  public @NotEmpty String getName() {
    return NAME;
  }

  @Override
  public @NotNull List<? extends ClassDefinition> getClassDefinitions() {
    return classDefs;
  }

  @Override
  public @NotEmpty String getLegacyName() {
    return NAME;
  }

}
