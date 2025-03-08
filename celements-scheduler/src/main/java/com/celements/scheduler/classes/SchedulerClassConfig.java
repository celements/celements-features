package com.celements.scheduler.classes;

import javax.inject.Inject;

import org.springframework.stereotype.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.WikiReference;

import com.celements.model.context.ModelContext;
import com.celements.scheduler.classdefs.SchedulerJobClass;

/**
 * @deprecated instead use {@link SchedulerJobClass}
 * @since 6.5
 */

@Deprecated(since = "6.5", forRemoval = true)
@Component
public class SchedulerClassConfig implements ISchedulerClassConfig {

  private final ModelContext modelContext;

  @Inject
  public SchedulerClassConfig(
      ModelContext modelContext) {
    this.modelContext = modelContext;
  }

  @Override
  public DocumentReference getSchedulerJobClassRef() {
    return getSchedulerJobClassRef(modelContext.getWikiRef());
  }

  @Override
  public DocumentReference getSchedulerJobClassRef(WikiReference wikiRef) {
    return new DocumentReference(CLASS_SCHEDULER_JOB_NAME, new SpaceReference(XWIKI_SPACE,
        wikiRef));
  }

}
