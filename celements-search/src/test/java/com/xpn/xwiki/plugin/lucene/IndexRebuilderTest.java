package com.xpn.xwiki.plugin.lucene;

import static com.celements.common.test.CelementsTestUtils.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;

import com.celements.common.test.AbstractComponentTest;
import com.celements.model.access.IModelAccessFacade;
import com.celements.model.context.ModelContext;
import com.celements.model.metadata.DocumentMetaData;
import com.celements.model.util.ModelUtils;
import com.celements.search.lucene.index.rebuild.LuceneIndexRebuildService;
import com.celements.store.DocumentCacheStore;

public class IndexRebuilderTest extends AbstractComponentTest {

  private IndexRebuilder rebuilder;
  private IndexUpdater updater;
  private DocumentCacheStore store;
  private RAMDirectory directory;
  private IndexWriter writer;
  private final List<AbstractIndexData> queued = new CopyOnWriteArrayList<>();

  @Before
  public void prepareTest() throws Exception {
    registerComponentMocks(IModelAccessFacade.class, ModelContext.class);
    expect(getMock(ModelContext.class).getXWikiContext()).andReturn(getContext()).anyTimes();
    rebuilder = (IndexRebuilder) getBeanFactory().getBean(LuceneIndexRebuildService.class);
    updater = createDefaultMock(IndexUpdater.class);
    store = createDefaultMock(DocumentCacheStore.class);
    directory = new RAMDirectory();
    writer = new IndexWriter(directory, new IndexWriterConfig(Version.LUCENE_34,
        new KeywordAnalyzer()));
    expect(getWikiMock().ParamAsLong(IndexRebuilder.PROP_MAX_QUEUE_SIZE, 1000))
        .andReturn(1000L);
    expect(getWikiMock().ParamAsLong(IndexRebuilder.PROP_PAUSE_DURATION, 10)).andReturn(10L);
    expect(getWikiMock().getStore()).andReturn(store).anyTimes();
    expect(updater.getDirectory()).andReturn(directory).anyTimes();
    expect(updater.getQueueSize()).andReturn(0L).anyTimes();
    updater.queue(anyObject(AbstractIndexData.class));
    expectLastCall().andAnswer(() -> {
      queued.add(getCurrentArgument(0));
      return null;
    }).anyTimes();
  }

  @After
  public void cleanup() throws Exception {
    if (rebuilder != null) {
      ((ExecutorService) ReflectionTestUtils.getField(rebuilder, "rebuildExecutor"))
          .shutdownNow();
    }
    if (writer != null) {
      writer.close();
    }
    if (directory != null) {
      directory.close();
    }
  }

  @Test
  public void test_rebuild_wikiScopeWithMixedCaseDocuments() throws Exception {
    DocumentReference ref = new DocumentReference("wiki", "MySpace", "MyDoc");
    assertScopedCleanup(ref.getWikiReference(), ref,
        new DocumentReference("otherwiki", "MySpace", "MyDoc"));
  }

  @Test
  public void test_rebuild_mixedCaseSpace() throws Exception {
    DocumentReference ref = new DocumentReference("wiki", "MySpace", "doc");
    assertScopedCleanup(ref.getLastSpaceReference(), ref,
        new DocumentReference("wiki", "myspace", "doc"));
  }

  @Test
  public void test_rebuild_mixedCaseDocument() throws Exception {
    DocumentReference ref = new DocumentReference("wiki", "space", "MyDoc");
    assertScopedCleanup(ref, ref, new DocumentReference("wiki", "space", "mydoc"));
  }

  @Test
  public void test_rebuild_lowercaseDocument() throws Exception {
    DocumentReference ref = new DocumentReference("wiki", "space", "doc");
    assertScopedCleanup(ref, ref, new DocumentReference("wiki", "space", "other"));
  }

  @Test
  public void test_rebuild_removesDeletedDocumentAndKeepsExistingDocument() throws Exception {
    DocumentReference existing = new DocumentReference("MyWiki", "MySpace", "Existing");
    DocumentReference deleted = new DocumentReference("MyWiki", "MySpace", "Deleted");
    DocumentReference outside = new DocumentReference("MyWiki", "OtherSpace", "Deleted");
    EntityReference scope = existing.getLastSpaceReference();
    addDocument(existing);
    addDocument(deleted);
    addDocument(outside);
    DocumentMetaData metaData = createDefaultMock(DocumentMetaData.class);
    expect(metaData.getDocRef()).andReturn(existing).anyTimes();
    expect(metaData.getLanguage()).andReturn("").anyTimes();
    expect(store.listDocumentMetaData(scope)).andReturn(Set.of(metaData));
    expect(getMock(IModelAccessFacade.class).getDocument(existing, ""))
        .andReturn(new TestXWikiDocument(existing));

    runRebuild(scope, 1);

    assertEquals(Set.of(docId(deleted)), deletedIds());
    assertEquals(1, queued.stream().filter(data -> !data.isDeleted()).count());
    assertEquals(Set.of(docId(existing), docId(outside)), remainingIds());
  }

  private void assertScopedCleanup(EntityReference scope, DocumentReference deleted,
      DocumentReference outside) throws Exception {
    addDocument(deleted);
    addDocument(outside);
    expect(store.listDocumentMetaData(scope)).andReturn(Set.of());

    runRebuild(scope, 0);

    assertEquals(Set.of(docId(deleted)), deletedIds());
    assertEquals(Set.of(docId(outside)), remainingIds());
  }

  private void runRebuild(EntityReference scope, long expectedCount) throws Exception {
    writer.commit();
    replayDefault();
    rebuilder.initialize(updater);
    assertEquals(Long.valueOf(expectedCount),
        rebuilder.startIndexRebuild(scope).get(10, TimeUnit.SECONDS));
    verifyDefault();
    for (AbstractIndexData data : queued) {
      if (data.isDeleted()) {
        writer.deleteDocuments(data.getTerm());
      }
    }
    writer.commit();
  }

  private Set<String> deletedIds() {
    return queued.stream().filter(AbstractIndexData::isDeleted).map(AbstractIndexData::getId)
        .collect(Collectors.toSet());
  }

  private Set<String> remainingIds() throws Exception {
    Set<String> ids = new HashSet<>();
    try (IndexSearcher searcher = new IndexSearcher(directory, true)) {
      for (ScoreDoc hit : searcher.search(new MatchAllDocsQuery(), 10).scoreDocs) {
        ids.add(searcher.doc(hit.doc).get(IndexFields.DOCUMENT_ID));
      }
    }
    return ids;
  }

  private void addDocument(DocumentReference ref) throws Exception {
    Document doc = new Document();
    doc.add(new Field(IndexFields.DOCUMENT_ID, docId(ref), Field.Store.YES,
        Field.Index.NOT_ANALYZED));
    doc.add(new Field(IndexFields.DOCUMENT_WIKI, ref.getWikiReference().getName(), Field.Store.YES,
        Field.Index.NOT_ANALYZED));
    doc.add(new Field(IndexFields.DOCUMENT_SPACE_S, ref.getLastSpaceReference().getName(),
        Field.Store.YES, Field.Index.NOT_ANALYZED));
    doc.add(new Field(IndexFields.DOCUMENT_NAME_S, ref.getName(), Field.Store.YES,
        Field.Index.NOT_ANALYZED));
    writer.addDocument(doc);
  }

  private String docId(DocumentReference ref) {
    return getBeanFactory().getBean(ModelUtils.class).serializeRef(ref) + ".default";
  }

}
