package com.celements.search.lucene.observation;

import static com.celements.common.test.CelementsTestUtils.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.xwiki.bridge.event.DocumentCreatedEvent;
import org.xwiki.bridge.event.DocumentDeletedEvent;
import org.xwiki.bridge.event.DocumentUpdatedEvent;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.observation.EventListener;

import com.celements.common.observation.listener.AbstractEventListener;
import com.celements.common.test.AbstractComponentTest;
import com.celements.search.lucene.ILuceneIndexService;
import com.celements.search.lucene.index.queue.IndexQueuePriority;
import com.celements.search.lucene.index.queue.QueueTask;
import com.google.common.collect.ImmutableList;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.plugin.lucene.TestXWikiDocument;
import com.xpn.xwiki.web.Utils;

public class QueueDocumentEventConverterTest extends AbstractComponentTest {

  private QueueDocumentEventConverter listener;

  @Before
  public void prepareTest() throws Exception {
    registerComponentMock(ILuceneIndexService.class);
    listener = (QueueDocumentEventConverter) Utils.getComponent(EventListener.class,
        QueueDocumentEventConverter.NAME);
  }

  @Test
  public void test_remote() {
    assertTrue("needs to listen on local events only, LuceneQueueEvents are distributed remotely",
        listener instanceof AbstractEventListener);
  }

  @Test
  public void test_getEvents() {
    assertEquals(3, listener.getEvents().size());
    assertSame(DocumentCreatedEvent.class, listener.getEvents().get(0).getClass());
    assertSame(DocumentUpdatedEvent.class, listener.getEvents().get(1).getClass());
    assertSame(DocumentDeletedEvent.class, listener.getEvents().get(2).getClass());
  }

  @Test
  public void test_onEvent_created() throws Exception {
    DocumentReference docRef = new DocumentReference("wiki", "space", "doc");
    XWikiDocument doc = new XWikiDocument(docRef);
    QueueTask docTask = createQueueTaskMock(null);
    expect(getMock(ILuceneIndexService.class).indexTask(docRef)).andReturn(docTask);

    replayDefault(docTask);
    listener.onEvent(new DocumentCreatedEvent(), doc, null);
    verifyDefault(docTask);
  }

  @Test
  public void test_onEvent_updated() throws Exception {
    DocumentReference docRef = new DocumentReference("wiki", "space", "doc");
    XWikiDocument doc = new XWikiDocument(docRef);
    QueueTask docTask = createQueueTaskMock(null);
    expect(getMock(ILuceneIndexService.class).indexTask(docRef)).andReturn(docTask);

    replayDefault(docTask);
    listener.onEvent(new DocumentUpdatedEvent(), doc, null);
    verifyDefault(docTask);
  }

  @Test
  public void test_onEvent_deleted() throws Exception {
    DocumentReference docRef = new DocumentReference("wiki", "space", "doc");
    XWikiDocument doc = new XWikiDocument(docRef);
    QueueTask docTask = createQueueTaskMock(null);
    expect(getMock(ILuceneIndexService.class).deleteTask(docRef)).andReturn(docTask);

    replayDefault(docTask);
    listener.onEvent(new DocumentDeletedEvent(), doc, null);
    verifyDefault(docTask);
  }

  @Test
  public void test_onEvent_withAtts() throws Exception {
    DocumentReference docRef = new DocumentReference("wiki", "space", "doc");
    XWikiDocument doc = new XWikiDocument(docRef);
    AttachmentReference attRef = new AttachmentReference("file", docRef);
    XWikiAttachment att = new XWikiAttachment(doc, attRef.getName());
    doc.setAttachmentList(ImmutableList.of(att));
    QueueTask docTask = createQueueTaskMock(null);
    expect(getMock(ILuceneIndexService.class).indexTask(docRef)).andReturn(docTask);
    QueueTask attTask = createQueueTaskMock(IndexQueuePriority.LOW);
    expect(getMock(ILuceneIndexService.class).indexTask(attRef)).andReturn(attTask);

    replayDefault(docTask, attTask);
    listener.onEvent(new DocumentUpdatedEvent(), doc, null);
    verifyDefault(docTask, attTask);
  }

  @Test
  public void test_onEvent_updated_withAtts_unchangedDocumentData() throws Exception {
    DocumentReference docRef = new DocumentReference("wiki", "space", "doc");
    XWikiDocument doc = new TestXWikiDocument(docRef);
    doc.setOriginalDocument(new TestXWikiDocument(docRef));
    doc.setAttachmentList(ImmutableList.of(new XWikiAttachment(doc, "file")));
    QueueTask docTask = createQueueTaskMock(null);
    expect(getMock(ILuceneIndexService.class).indexTask(docRef)).andReturn(docTask);

    replayDefault(docTask);
    listener.onEvent(new DocumentUpdatedEvent(), doc, null);
    verifyDefault(docTask);
  }

  @Test
  public void test_onEvent_updated_withAtts_changedDocumentData() throws Exception {
    DocumentReference docRef = new DocumentReference("wiki", "space", "doc");
    TestXWikiDocument doc = new TestXWikiDocument(docRef);
    TestXWikiDocument original = new TestXWikiDocument(docRef);
    doc.setOriginalDocument(original);
    doc.setTitle("new title");
    doc.setAttachmentList(ImmutableList.of(new XWikiAttachment(doc, "file")));
    QueueTask docTask = createQueueTaskMock(null);
    expect(getMock(ILuceneIndexService.class).indexTask(docRef)).andReturn(docTask);
    QueueTask attTask = createQueueTaskMock(IndexQueuePriority.LOW);
    expect(getMock(ILuceneIndexService.class).indexTask(new AttachmentReference("file", docRef)))
        .andReturn(attTask);

    replayDefault(docTask, attTask);
    listener.onEvent(new DocumentUpdatedEvent(), doc, null);
    verifyDefault(docTask, attTask);
  }

  @Test
  public void test_onEvent_contextDoc() throws Exception {
    DocumentReference docRef = new DocumentReference("wiki", "space", "doc");
    XWikiDocument doc = new XWikiDocument(docRef);
    getContext().setDoc(doc);
    QueueTask mock = createQueueTaskMock(IndexQueuePriority.HIGHEST);
    expect(getMock(ILuceneIndexService.class).indexTask(docRef)).andReturn(mock);

    replayDefault(mock);
    listener.onEvent(new DocumentUpdatedEvent(), doc, null);
    verifyDefault(mock);
  }

  @Test
  public void test_onEvent_lang() throws Exception {
    DocumentReference docRef = new DocumentReference("wiki", "space", "doc");
    XWikiDocument doc = new XWikiDocument(docRef);
    doc.setLanguage("en");
    QueueTask docTask = createQueueTaskMock(null);
    Capture<DocumentReference> docRefCpt = EasyMock.newCapture();
    expect(getMock(ILuceneIndexService.class).indexTask(capture(docRefCpt))).andReturn(docTask);

    replayDefault(docTask);
    listener.onEvent(new DocumentUpdatedEvent(), doc, null);
    verifyDefault(docTask);

    assertEquals(docRef, docRefCpt.getValue());
    assertEquals(QueueLangDocumentReference.class, docRefCpt.getValue().getClass());
    assertEquals("en", ((QueueLangDocumentReference) docRefCpt.getValue()).getLang().orElse(null));
  }

  @Test
  public void test_onEvent_lang_noneSet() throws Exception {
    DocumentReference docRef = new DocumentReference("wiki", "space", "doc");
    XWikiDocument doc = new XWikiDocument(docRef);
    QueueTask docTask = createQueueTaskMock(null);
    Capture<DocumentReference> docRefCpt = EasyMock.newCapture();
    expect(getMock(ILuceneIndexService.class).indexTask(capture(docRefCpt))).andReturn(docTask);

    replayDefault(docTask);
    listener.onEvent(new DocumentUpdatedEvent(), doc, null);
    verifyDefault(docTask);

    assertEquals(docRef, docRefCpt.getValue());
    assertEquals(QueueLangDocumentReference.class, docRefCpt.getValue().getClass());
    assertNull(((QueueLangDocumentReference) docRefCpt.getValue()).getLang().orElse(null));
  }

  private QueueTask createQueueTaskMock(IndexQueuePriority prio) {
    QueueTask mock = createMock(QueueTask.class);
    expect(mock.getReference()).andReturn(null).anyTimes();
    expect(mock.priority(prio)).andReturn(mock);
    mock.queue();
    return mock;
  }

}
