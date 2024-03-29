/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.celements.scheduler.job;

import static com.celements.execution.XWikiExecutionProp.*;
import static com.google.common.base.Preconditions.*;
import static com.google.common.base.Strings.*;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.context.ExecutionContextException;
import org.xwiki.context.ExecutionContextManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.velocity.VelocityManager;

import com.celements.model.access.IModelAccessFacade;
import com.celements.model.access.exception.DocumentNotExistsException;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.web.Utils;

/**
 * Base class for any XWiki Quartz Job. This class take care of initializing ExecutionContext
 * properly.
 * <p>
 * A class extending {@link AbstractJob} should implements {@link #executeJob(JobExecutionContext)}.
 *
 * @since 2.90
 * @author fabian pichler
 */
public abstract class AbstractJob implements Job {

  protected final Logger logger = LoggerFactory.getLogger(this.getClass());

  protected final Supplier<Execution> execution = () -> Utils.getComponent(Execution.class);
  protected final Supplier<ExecutionContextManager> executionContextManager = () -> Utils
      .getComponent(ExecutionContextManager.class);
  protected final Supplier<VelocityManager> velocityManager = () -> Utils
      .getComponent(VelocityManager.class);
  protected final Supplier<List<PostJobAction>> postJobActions = () -> Utils
      .getComponentList(PostJobAction.class);
  protected final Supplier<IModelAccessFacade> modelAccess = () -> Utils
      .getComponent(IModelAccessFacade.class);

  @Override
  public final void execute(JobExecutionContext jobContext) throws JobExecutionException {
    JobDataMap data = checkNotNull(jobContext.getJobDetail().getJobDataMap());
    try {
      initExecutionContext(data);
      executeJob(jobContext);
    } catch (Throwable exp) {
      getLogger().error("Exception thrown during job '{}' execution",
          jobContext.getJobDetail().getFullName(), exp);
      throw new JobExecutionException("Failed to execute job '"
          + jobContext.getJobDetail().getFullName() + "'", exp);
    } finally {
      // We must ensure we clean the ThreadLocal variables located in the Execution
      // component as otherwise we will have a potential memory leak.
      execution.get().removeContext();
      postJobActions.get().forEach(runnable -> {
        try {
          runnable.accept(jobContext);
        } catch (Exception exc) {
          getLogger().error("failed to execute [{}]", runnable, exc);
        }
      });
    }
  }

  ExecutionContext initExecutionContext(JobDataMap data)
      throws ExecutionContextException, DocumentNotExistsException {
    ExecutionContext context = createEContextForJob(data);
    executionContextManager.get().initialize(context);
    prepareXContextForJob(data);
    setJobDoc(data); // modelAccess only usable after initialize and setting user
    return context;
  }

  /**
   * create the execution context with job defaults to allow proper initialization by the
   * ExecutionContextManager.
   */
  private ExecutionContext createEContextForJob(JobDataMap data) {
    ExecutionContext context = new ExecutionContext();
    execution.get().setContext(context);
    context.set(WIKI, Optional.ofNullable(emptyToNull(data.getString("jobDatabase")))
        .map(WikiReference::new)
        .orElseGet(((DocumentReference) data.get("jobDocRef"))::getWikiReference));
    context.set(XWIKI_REQUEST_ACTION, "view");
    return context;
  }

  /**
   * Feed the stub xwiki context created by the ExecutionContextManager with additional job data for
   * the job execution thread.
   */
  private void prepareXContextForJob(JobDataMap data) {
    XWikiContext xcontext = getXContext();
    String jobUser = data.getString("jobUser");
    xcontext.setUser(jobUser.isEmpty() ? "XWiki.Scheduler" : jobUser);
    String jobLang = data.getString("jobLang");
    if (!jobLang.isEmpty()) {
      xcontext.setLanguage(jobLang);
    }
    velocityManager.get().getVelocityContext();
  }

  private void setJobDoc(JobDataMap data) throws DocumentNotExistsException {
    XWikiDocument jobDoc = modelAccess.get().getDocument((DocumentReference) data.get("jobDocRef"));
    getEContext().set(DOC, jobDoc);
    getXContext().setDoc(jobDoc);
  }

  protected ExecutionContext getEContext() {
    return execution.get().getContext();
  }

  protected XWikiContext getXContext() {
    return getEContext().get(XWIKI_CONTEXT).orElseThrow(IllegalStateException::new);
  }

  protected Logger getLogger() {
    return logger;
  }

  protected abstract void executeJob(JobExecutionContext jobContext) throws JobExecutionException;

}
