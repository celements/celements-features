package com.celements.store.s3.att.migration;

import static com.celements.logging.LogUtils.*;

import java.util.List;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.WikiReference;

import com.celements.init.XWikiProvider;
import com.celements.model.access.IModelAccessFacade;
import com.celements.model.util.ModelUtils;
import com.celements.query.QueryExecutionService;
import com.celements.store.s3.att.S3AttachmentContentStore;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.store.AttachmentContentStore.AttachmentContentStoreException;
import com.xpn.xwiki.store.AttachmentVersioningStore;

import one.util.streamex.StreamEx;

@Service
@Lazy
public class S3AttachmentContentMigrationService {

  static final Logger LOGGER = LoggerFactory.getLogger(S3AttachmentContentMigrationService.class);

  private final QueryExecutionService queryExecutor;
  private final S3AttachmentContentStore s3AttStore;
  private final IModelAccessFacade modelAccess;
  private final ModelUtils modelUtils;
  private final XWikiProvider xwikiProvider;

  @Inject
  public S3AttachmentContentMigrationService(
      QueryExecutionService queryExecutor,
      S3AttachmentContentStore s3AttStore,
      IModelAccessFacade modelAccess,
      ModelUtils modelUtils,
      XWikiProvider xwikiProvider) {
    this.queryExecutor = queryExecutor;
    this.s3AttStore = s3AttStore;
    this.modelAccess = modelAccess;
    this.modelUtils = modelUtils;
    this.xwikiProvider = xwikiProvider;
  }

  public void migrate(WikiReference wiki) throws XWikiException, AttachmentContentStoreException {
    migrateArchive(wiki);
    // migrateRecycleBin(wiki); // TODO implement xwikiattrecyclebin migration
  }

  public void migrateArchive(WikiReference wiki)
      throws XWikiException, AttachmentContentStoreException {
    var result = queryExecutor.executeReadSql(wiki, String.class, getSqlAttachmentsWithContent());
    LOGGER.info("[{}] migrating {} attachments to S3 store", wiki.getName(), result.size());
    var count = 0;
    var countContents = 0;
    var processed = 0;
    for (List<String> row : result) {
      var docRef = modelUtils.resolveRef(row.get(0), DocumentReference.class, wiki);
      var fileName = row.get(1);
      var att = modelAccess.getOrCreateDocument(docRef).getAttachment(fileName);
      if (att != null) {
        try {
          countContents += migrate(att);
          count++;
        } catch (XWikiException exc) {
          LOGGER.warn("[{}] failed migrating attachment: {}",
              wiki.getName(), serialize(docRef) + "@" + fileName, exc);
        }
      }
      processed++;
      if ((processed % 100) == 0) {
        LOGGER.debug("[{}] processed {}/{} attachments", wiki.getName(), processed, result.size());
      }
    }
    LOGGER.info("[{}] migrated {} attachments with {} contents to S3 store",
        wiki.getName(), count, countContents);
  }

  private static String getSqlAttachmentsWithContent() {
    return "SELECT DISTINCT d.XWD_FULLNAME, a.XWA_FILENAME "
        + "FROM xwikidoc d "
        + "JOIN xwikiattachment a ON d.XWD_ID = a.XWA_DOC_ID "
        + "JOIN xwikiattachment_content c ON a.XWA_ID = c.XWA_ID";
  }

  public int migrate(XWikiAttachment att) throws XWikiException, AttachmentContentStoreException {
    var count = migrateArchive(att);
    if (count > 0) { // content moved to s3, let's rebuild the archive without content blobs
      var archive = att.loadArchive();
      archive.rebuildArchive(false);
      getAttachmentVersioningStore().saveArchive(archive, false);
    }
    return count;
  }

  public int migrateArchive(XWikiAttachment att)
      throws XWikiException, AttachmentContentStoreException {
    var count = 0;
    var archive = att.loadArchive();
    for (var v : StreamEx.of(archive.getVersions()).append(att.getRCSVersion()).distinct()) {
      try {
        if (pushToS3(archive.getRevision(v))) {
          count++;
        }
      } catch (XWikiException exc) {
        throw new XWikiException(0, 0, "Failed migrating " +
            serialize(att.getAttachmentReference()) + "@" + v, exc);
      }
    }
    LOGGER.trace("[{}] migrated {} with {} contents",
        defer(() -> att.getWikiReference().getName()),
        defer(() -> serialize(att.getAttachmentReference())),
        count);
    return count;
  }

  private boolean pushToS3(XWikiAttachment att)
      throws XWikiException, AttachmentContentStoreException {
    if (!s3AttStore.hasContent(att)) {
      LOGGER.trace("[{}] pushToS3 {}",
          defer(() -> att.getWikiReference().getName()),
          defer(() -> s3AttStore.buildS3AttachmentVersionKey(att)));
      s3AttStore.saveContent(att.loadContent());
      return true;
    }
    return false;
  }

  private String serialize(EntityReference ref) {
    return modelUtils.serializeRefLocal(ref);
  }

  private AttachmentVersioningStore getAttachmentVersioningStore() {
    return xwikiProvider.get().orElseThrow(IllegalStateException::new)
        .getAttachmentStore()
        .getVersioningStore();
  }
}
