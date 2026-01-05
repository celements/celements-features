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
package com.xpn.xwiki.plugin.lucene;

import static com.google.common.collect.ImmutableMap.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.observation.ObservationManager;

import com.celements.common.observation.event.AbstractEntityEvent;
import com.celements.model.access.exception.DocumentNotExistsException;
import com.celements.model.context.ModelContext;
import com.celements.model.util.ModelUtils;
import com.celements.model.util.References;
import com.celements.search.lucene.index.queue.IndexQueuePriority;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.xpn.xwiki.XWikiConfigSource;
import com.xpn.xwiki.plugin.lucene.indexExtension.ILuceneIndexExtensionServiceRole;
import com.xpn.xwiki.plugin.lucene.observation.event.LuceneDocumentDeletedEvent;
import com.xpn.xwiki.plugin.lucene.observation.event.LuceneDocumentDeletingEvent;
import com.xpn.xwiki.plugin.lucene.observation.event.LuceneDocumentIndexedEvent;
import com.xpn.xwiki.plugin.lucene.observation.event.LuceneDocumentIndexingEvent;
import com.xpn.xwiki.util.AbstractXWikiRunnable;
import com.xpn.xwiki.web.Utils;

/**
 * @version $Id: ced4ee86b2d2cf5830598a4a3aefcea8394d60e6 $
 */
public class IndexUpdater {

  private static final Logger LOGGER = LoggerFactory.getLogger(IndexUpdater.class);

  static final String PROP_INDEXING_INTERVAL = "xwiki.plugins.lucene.indexinterval";

  static final String PROP_COMMIT_INTERVAL = "xwiki.plugins.lucene.commitinterval";

  /**
   * Collecting all the fields for using up in search
   */
  private static final Set<String> COLLECTED_FIELDS = Collections.newSetFromMap(
      new ConcurrentHashMap<String, Boolean>());

  final LucenePlugin plugin;

  final IndexWriter writer;

  /**
   * Milliseconds of sleep between checks for changed documents.
   */
  private final long indexingInterval;
  private final long commitInterval;

  private final int indexThreadMaxCount;
  private final List<ScheduledFuture<?>> indexFutures = new ArrayList<>();

  private final ImmutableMap<IndexQueuePriority, XWikiDocumentQueue> queues = Stream
      .of(IndexQueuePriority.values())
      .sorted(Ordering.natural().reversed())
      .collect(toImmutableMap(prio -> prio, prio -> new XWikiDocumentQueue()));

  private final ScheduledExecutorService indexExecutor;
  private final ScheduledExecutorService commitExecutor;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicBoolean optimize = new AtomicBoolean(false);
  private final AtomicBoolean commit = new AtomicBoolean(false);

  IndexUpdater(IndexWriter writer, LucenePlugin plugin) {
    this.plugin = plugin;
    indexThreadMaxCount = 8;
    indexExecutor = Executors.newScheduledThreadPool(indexThreadMaxCount,
        new ThreadFactoryBuilder().setNameFormat("lucene-indexer-%d").build());
    commitExecutor = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("lucene-commiter-%d").build());
    indexingInterval = 1000L * Optional.ofNullable(Longs.tryParse(getXWikiCfg()
        .getProperty(PROP_INDEXING_INTERVAL))).orElse(30L);
    commitInterval = Optional.ofNullable(Longs.tryParse(getXWikiCfg()
        .getProperty(PROP_COMMIT_INTERVAL))).orElse(5000L);
    this.writer = writer;
  }

  public synchronized void start() {
    if (!running.compareAndSet(false, true)) {
      return;
    }
    scheduleIndexRunner();
    commitExecutor.scheduleWithFixedDelay(new CommitRunner(),
        commitInterval, commitInterval, TimeUnit.MILLISECONDS);
  }

  private synchronized ScheduledFuture<?> scheduleIndexRunner() {
    LOGGER.debug("scheduling runner #{}", indexFutures.size());
    var future = indexExecutor.scheduleWithFixedDelay(new IndexRunner(),
        0, indexingInterval, TimeUnit.MILLISECONDS);
    indexFutures.add(future);
    return future;
  }

  private synchronized void tryScheduleIndexRunner() {
    // only schedule if we are below max thread count and the queue size justifies it
    if (isRunning() && (indexFutures.size() < indexThreadMaxCount) && hasQueueRunaway(100)) {
      scheduleIndexRunner();
    }
  }

  private synchronized boolean tryUnscheduleIndexRunner() {
    // only unschedule if there is more than one index runner and the queue size justifies it
    if ((indexFutures.size() > 1) && !hasQueueRunaway(50)) {
      var idx = indexFutures.size() - 1;
      LOGGER.debug("unscheduling runner #{}", idx);
      ScheduledFuture<?> future = indexFutures.remove(idx);
      return future.cancel(false);
    }
    return false;
  }

  private synchronized boolean hasQueueRunaway(long factor) {
    return getQueueSize() >= (indexFutures.size() * factor);
  }

  public boolean isRunning() {
    return running.get();
  }

  /**
   * if exit is being set, the IndexUpdater will no longer accept new queue entries, finishes
   * processing the queue and then shut down gracefully
   */
  public void stop() {
    if (!running.compareAndSet(true, false)) {
      return;
    }
    LOGGER.info("stop called");
    indexExecutor.shutdownNow();
    commitExecutor.shutdownNow();
    indexFutures.clear();
    if (commit.get()) {
      try {
        writer.commit();
      } catch (IOException e) {
        LOGGER.error("final commit failed during shutdown", e);
      }
    }
  }

  public void flagCommit() {
    commit.set(true);
  }

  public void flagOptimize() {
    optimize.set(true);
  }

  class CommitRunner extends AbstractXWikiRunnable {

    @Override
    protected void runInternal() {
      tryUnscheduleIndexRunner();
      if (!commit.compareAndSet(true, false)) {
        return;
      }
      try {
        LOGGER.debug("commit");
        writer.commit();
        plugin.closeSearcherProvider();
        if (optimize.compareAndSet(true, false)) {
          LOGGER.warn("started optimizing lucene index");
          writer.optimize();
          LOGGER.warn("finished optimizing lucene index");
        }
      } catch (IOException e) {
        commit.set(true); // rearm commit flag
        LOGGER.error("Failed to commit lucene index", e);
      }
    }
  }

  class IndexRunner extends AbstractXWikiRunnable {

    @Override
    protected void runInternal() {
      if (!isRunning() || Thread.currentThread().isInterrupted()) {
        return;
      }
      try {
        LOGGER.trace("start");
        getContext().setWikiRef(getModelUtils().getMainWikiRef());
        var count = pollIndexQueue();
        LOGGER.trace("done, indexed {} docs", count);
      } catch (Exception exc) {
        LOGGER.error("Unexpected error occured", exc);
        stop();
      }
    }

    private long pollIndexQueue() {
      long count = -1;
      Optional<AbstractIndexData> next;
      do {
        count++;
        next = queues().filter(q -> !q.isEmpty()).findFirst().map(XWikiDocumentQueue::remove);
        next.ifPresent(this::indexData);
      } while (next.isPresent());
      return count;
    }

    private void indexData(AbstractIndexData data) {
      try {
        LOGGER.trace("indexData: start [{}]", data.getEntityReference());
        getContext().setWikiRef(References.extractRef(data.getEntityReference(),
            WikiReference.class).or(getContext().getWikiRef()));
        if (data.isDeleted()) {
          removeFromIndex(data);
        } else {
          try {
            addToIndex(data);
          } catch (DocumentNotExistsException dne) {
            LOGGER.info("indexData: removing inexistent [{}]", data.getEntityReference(), dne);
            removeFromIndex(data);
          }
        }
        flagCommit();
        LOGGER.trace("indexData: finished [{}]", data.getEntityReference());
      } catch (Exception exc) {
        LOGGER.warn("indexData: error [{}], {}: {}", data, exc.getClass(), exc.getMessage(), exc);
      }
    }

    private void addToIndex(AbstractIndexData data) throws IOException, DocumentNotExistsException {
      LOGGER.trace("addToIndex: '{}'", data);
      EntityReference ref = data.getEntityReference();
      notify(data, new LuceneDocumentIndexingEvent(ref));
      Document luceneDoc = new Document();
      data.addDataToLuceneDocument(luceneDoc);
      getLuceneExtensionService().extend(data, luceneDoc);
      collectFields(luceneDoc);
      writer.updateDocument(data.getTerm(), luceneDoc);
      notify(data, new LuceneDocumentIndexedEvent(ref));
    }

    // collecting all the fields for using up in search
    // FIXME (Marc Sladek) this doesn't work after restarts as long as there was no doc indexed with
    // the required fields, move to database instead of ram? or is there another solution to the
    // problem it tries to solve?
    private void collectFields(Document luceneDoc) {
      for (Fieldable field : luceneDoc.getFields()) {
        COLLECTED_FIELDS.add(field.name());
      }
    }

    private void removeFromIndex(AbstractIndexData data) throws IOException {
      LOGGER.trace("removeFromIndex: '{}'", data);
      EntityReference ref = data.getEntityReference();
      if (ref != null) {
        notify(data, new LuceneDocumentDeletingEvent(ref));
      }
      writer.deleteDocuments(data.getTerm());
      if (ref != null) {
        notify(data, new LuceneDocumentDeletedEvent(ref));
      }
    }

    private void notify(AbstractIndexData data, AbstractEntityEvent event) {
      if (data.notifyObservationEvents()) {
        Utils.getComponent(ObservationManager.class).notify(event, event.getReference(),
            getContext().getXWikiContext());
      } else {
        LOGGER.trace("skip notify '{}' for '{}'", event, data);
      }
    }
  }

  public void queue(AbstractIndexData data) {
    if (isRunning()) {
      LOGGER.trace("queue{}: '{}'", (data.isDeleted() ? " delete" : ""), data.getId());
      queues.get(data.getPriority()).add(data);
      tryScheduleIndexRunner();
    } else {
      throw new IllegalStateException("IndexUpdater has been shut down");
    }
  }

  /**
   * @return the number of documents in all queues.
   */
  public long getQueueSize() {
    return queues().mapToInt(XWikiDocumentQueue::getSize).sum();
  }

  /**
   * @return the number of documents in the queue.
   */
  public long getQueueSize(@NotNull IndexQueuePriority priority) {
    return queues.get(priority).getSize();
  }

  /**
   * @return the number of documents in Lucene index writer.
   */
  // TODO why is writer used for this?
  public long getLuceneDocCount() {
    int n = -1;
    try {
      n = writer.numDocs();
    } catch (IOException e) {
      LOGGER.error("Failed to get the number of documents in Lucene index writer", e);
    }
    return n;
  }

  public Set<String> getCollectedFields() {
    return new HashSet<>(COLLECTED_FIELDS);
  }

  public Directory getDirectory() {
    return writer.getDirectory();
  }

  private Stream<XWikiDocumentQueue> queues() {
    return queues.values().stream();
  }

  private ILuceneIndexExtensionServiceRole getLuceneExtensionService() {
    return Utils.getComponent(ILuceneIndexExtensionServiceRole.class);
  }

  private ModelUtils getModelUtils() {
    return Utils.getComponent(ModelUtils.class);
  }

  private ModelContext getContext() {
    return Utils.getComponent(ModelContext.class);
  }

  private XWikiConfigSource getXWikiCfg() {
    return Utils.getComponent(XWikiConfigSource.class);
  }

}
