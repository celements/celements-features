package com.celements.search.lucene.observation;

import static com.celements.common.MoreObjectsCel.*;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.xwiki.bridge.event.DocumentCreatedEvent;
import org.xwiki.bridge.event.DocumentDeletedEvent;
import org.xwiki.bridge.event.DocumentUpdatedEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.observation.event.Event;
import org.xwiki.rendering.syntax.Syntax;

import com.celements.search.lucene.index.queue.IndexQueuePriority;
import com.google.common.collect.ImmutableList;
import com.xpn.xwiki.doc.XWikiDocument;

@Component(QueueDocumentEventConverter.NAME)
public class QueueDocumentEventConverter extends AbstractQueueEventConverter<XWikiDocument> {

  public static final String NAME = "LuceneQueueDocumentEventConverter";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public List<Event> getEvents() {
    return ImmutableList.of(
        new DocumentCreatedEvent(),
        new DocumentUpdatedEvent(),
        new DocumentDeletedEvent());
  }

  @Override
  protected boolean isDeleteEvent(Event event) {
    return tryCast(event, DocumentDeletedEvent.class).isPresent();
  }

  @Override
  protected Stream<EntityReference> getReferences(Event event, XWikiDocument doc) {
    Stream<EntityReference> docRef = Stream.of(
        new QueueLangDocumentReference(doc.getDocumentReference(), doc.getLanguage()));
    if ((event instanceof DocumentUpdatedEvent) && hasSameAttachmentDocumentData(doc)) {
      return docRef;
    }
    return Stream.concat(
        docRef,
        doc.getAttachmentList().stream()
            .filter(Objects::nonNull)
            .map(att -> new AttachmentReference(att.getFilename(), doc.getDocumentReference())));
  }

  private boolean hasSameAttachmentDocumentData(XWikiDocument doc) {
    XWikiDocument original = doc.getOriginalDocument();
    try {
      return (original != null)
          && Objects.equals(doc.getDocumentReference(), original.getDocumentReference())
          && Objects.equals(doc.getLanguage(), original.getLanguage())
          && Objects.equals(doc.isHidden(), original.isHidden())
          && Objects.equals(getRenderedTitle(doc), getRenderedTitle(original));
    } catch (RuntimeException exc) {
      LOGGER.warn("failed to compare attachment document data for [{}]", doc.getDocumentReference(),
          exc);
      return false;
    }
  }

  private String getRenderedTitle(XWikiDocument doc) {
    return doc.getRenderedTitle(Syntax.PLAIN_1_0, context.getXWikiContext());
  }

  @Override
  protected IndexQueuePriority getPriority(EntityReference ref) {
    if (ref.getType() == EntityType.ATTACHMENT) {
      return IndexQueuePriority.LOW;
    } else if (context.getCurrentDocRef().toJavaUtil().map(docRef -> docRef.equals(ref))
        .orElse(false)) {
      return IndexQueuePriority.HIGHEST;
    }
    return null;
  }

}
