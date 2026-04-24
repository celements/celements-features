package com.celements.search.lucene.observation;

import static com.celements.common.MoreObjectsCel.*;
import static com.celements.logging.LogUtils.*;
import static com.google.common.base.MoreObjects.*;

import java.util.List;
import java.util.Optional;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.observation.event.Event;
import org.xwiki.observation.remote.RemoteObservationManagerContext;

import com.celements.common.observation.listener.AbstractRemoteEventListener;
import com.celements.init.XWikiProvider;
import com.celements.model.access.exception.DocumentNotExistsException;
import com.celements.model.util.ModelUtils;
import com.celements.search.lucene.observation.event.LuceneQueueDeleteEvent;
import com.celements.search.lucene.observation.event.LuceneQueueEvent;
import com.celements.search.lucene.observation.event.LuceneQueueIndexEvent;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.plugin.lucene.AbstractIndexData;
import com.xpn.xwiki.plugin.lucene.AttachmentData;
import com.xpn.xwiki.plugin.lucene.DeleteData;
import com.xpn.xwiki.plugin.lucene.DocumentData;
import com.xpn.xwiki.plugin.lucene.LucenePlugin;
import com.xpn.xwiki.plugin.lucene.WikiData;

@Component(QueueEventListener.NAME)
public class QueueEventListener
    extends AbstractRemoteEventListener<EntityReference, LuceneQueueEvent.Data> {

  public static final String NAME = "celements.search.QueueEventListener";

  @Requirement
  private ModelUtils modelUtils;

  @Requirement
  private RemoteObservationManagerContext remoteObsManagerCtx;

  @Requirement
  private XWikiProvider xwikiProvider;

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public List<Event> getEvents() {
    return List.of(
        new LuceneQueueIndexEvent(),
        new LuceneQueueDeleteEvent());
  }

  @Override
  protected void onEventInternal(Event event, EntityReference ref,
      LuceneQueueEvent.Data eventData) {
    LuceneQueueEvent queueEvent = (LuceneQueueEvent) event;
    AbstractIndexData indexData = null;
    if (ref instanceof WikiReference wikiRef) {
      indexData = newWikiData(wikiRef, queueEvent.isDelete());
    } else if (queueEvent.isDelete()) {
      indexData = newDeleteData(ref);
    } else if (ref instanceof DocumentReference docRef) {
      indexData = newDocumentData(docRef);
    } else if (ref instanceof AttachmentReference attRef) {
      indexData = newAttachmentData(attRef);
    } else {
      LOGGER.warn("unable to queue ref [{}]", defer(() -> modelUtils.serializeRef(ref)));
    }
    queue(indexData, firstNonNull(eventData, LuceneQueueEvent.Data.DEFAULT));
  }

  private void queue(AbstractIndexData indexData, LuceneQueueEvent.Data eventData) {
    if (indexData != null) {
      indexData.setPriority(eventData.priority);
      boolean remote = remoteObsManagerCtx.isRemoteState();
      // remote queue replay must be terminal, otherwise async Lucene completion can start a new
      // local event cascade that broadcasts again.
      boolean disableNotifications = eventData.disableEventNotification || remote;
      indexData.setDisableObservationEventNotification(disableNotifications);
      getLucenePlugin().ifPresent(plugin -> {
        LOGGER.debug("queue: {}, remote={}", indexData, remote);
        plugin.queue(indexData);
      });
    }
  }

  WikiData newWikiData(WikiReference wiki, boolean delete) {
    return new WikiData(wiki, delete);
  }

  private AbstractIndexData newDocumentData(DocumentReference docRef) {
    try {
      return newDocumentData(modelAccess.getDocument(docRef, tryGetLang(docRef).orElse(null)));
    } catch (DocumentNotExistsException dne) {
      LOGGER.debug("can't queue inexistent document [{}]",
          defer(() -> modelUtils.serializeRef(docRef)));
      return null;
    }
  }

  DocumentData newDocumentData(XWikiDocument doc) {
    return new DocumentData(doc, false);
  }

  private AbstractIndexData newAttachmentData(AttachmentReference attRef) {
    XWikiAttachment att = modelAccess.getOrCreateDocument(attRef.getDocumentReference())
        .getAttachment(attRef.getName());
    if (att != null) {
      return newAttachmentData(att);
    } else {
      LOGGER.debug("can't queue inexistent attachment [{}]",
          defer(() -> modelUtils.serializeRef(attRef)));
      return null;
    }
  }

  AttachmentData newAttachmentData(XWikiAttachment att) {
    return new AttachmentData(att, false);
  }

  /**
   * docId for
   * doc: 'wiki:space.doc.en',
   * att: 'wiki:space.doc.file.att.jpg'
   */
  DeleteData newDeleteData(EntityReference ref) {
    final StringBuilder docId = new StringBuilder();
    docId.append(modelUtils.serializeRef(ref.extractRef(EntityType.DOCUMENT).orElse(ref)));
    tryCast(ref, DocumentReference.class).ifPresent(
        docRef -> docId.append('.').append(tryGetLang(docRef).orElse("default")));
    tryCast(ref, AttachmentReference.class).ifPresent(
        attRef -> docId.append(".file.").append(attRef.getName()));
    return new DeleteData(docId.toString());
  }

  private Optional<String> tryGetLang(DocumentReference docRef) {
    return tryCast(docRef, QueueLangDocumentReference.class)
        .map(QueueLangDocumentReference::getLang)
        .filter(Optional::isPresent).map(Optional::get);
  }

  private Optional<LucenePlugin> getLucenePlugin() {
    return xwikiProvider.get().map(xwiki -> (LucenePlugin) xwiki
        .getPlugin("lucene", context.getXWikiContext()));
  }

}
