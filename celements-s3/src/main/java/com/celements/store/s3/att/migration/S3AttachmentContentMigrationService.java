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
import com.xpn.xwiki.store.hibernate.HibernateAttachmentContentStore;

import one.util.streamex.StreamEx;

@Service
@Lazy
public class S3AttachmentContentMigrationService {

  static final Logger LOGGER = LoggerFactory.getLogger(S3AttachmentContentMigrationService.class);

  private final QueryExecutionService queryExecutor;
  private final S3AttachmentContentStore s3AttContentStore;
  private final HibernateAttachmentContentStore hibAttContentStore;
  private final IModelAccessFacade modelAccess;
  private final ModelUtils modelUtils;
  private final XWikiProvider xwikiProvider;

  @Inject
  public S3AttachmentContentMigrationService(
      QueryExecutionService queryExecutor,
      S3AttachmentContentStore s3AttContentStore,
      HibernateAttachmentContentStore hibAttContentStore,
      IModelAccessFacade modelAccess,
      ModelUtils modelUtils,
      XWikiProvider xwikiProvider) {
    this.queryExecutor = queryExecutor;
    this.s3AttContentStore = s3AttContentStore;
    this.hibAttContentStore = hibAttContentStore;
    this.modelAccess = modelAccess;
    this.modelUtils = modelUtils;
    this.xwikiProvider = xwikiProvider;
  }

  public void migrate(WikiReference wiki, boolean cleanup)
      throws XWikiException, AttachmentContentStoreException {
    migrateArchive(wiki, cleanup);
    // migrateRecycleBin(wiki); // TODO implement xwikiattrecyclebin migration
  }

  private static final String SQL_ATTACHMENTS_WITH_CONTENT = ""
      + "SELECT DISTINCT d.XWD_FULLNAME, a.XWA_FILENAME "
      + "FROM xwikidoc d "
      + "JOIN xwikiattachment a ON d.XWD_ID = a.XWA_DOC_ID "
      + "JOIN xwikiattachment_content c ON a.XWA_ID = c.XWA_ID";

  public void migrateArchive(WikiReference wiki, boolean cleanup)
      throws XWikiException, AttachmentContentStoreException {
    var result = queryExecutor.executeReadSql(wiki, String.class, SQL_ATTACHMENTS_WITH_CONTENT);
    LOGGER.info("[{}] migrating {} attachments to S3 store", wiki.getName(), result.size());
    var countPushed = 0;
    var countProcessed = 0;
    var countCleaned = 0;
    var countError = 0;
    for (List<String> row : result) {
      var docRef = modelUtils.resolveRef(row.get(0), DocumentReference.class, wiki);
      var fileName = row.get(1);
      var att = modelAccess.getOrCreateDocument(docRef).getAttachment(fileName);
      if (att != null) {
        try {
          countPushed += migrate(att);
          if (cleanup) {
            cleanup(att); // content moved to s3, cleanup content in db
            countCleaned++;
          }
        } catch (XWikiException exc) {
          countError++;
          LOGGER.error("[{}] failed migrating {}", wiki.getName(),
              serialize(att.getAttachmentReference()), exc);
        }
      }
      countProcessed++;
      if ((countProcessed % 100) == 0) {
        LOGGER.info("[{}] processed {}/{} attachments",
            wiki.getName(), countProcessed, result.size());
      }
    }
    LOGGER.info("[{}] migration finished: {} processed, {} failed, {} cleaned, {} pushed contents",
        wiki.getName(), countProcessed, countError, countPushed, countCleaned);
  }

  public int migrate(XWikiAttachment att)
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
    if (!s3AttContentStore.hasContent(att)) {
      LOGGER.trace("[{}] pushToS3 {}",
          defer(() -> att.getWikiReference().getName()),
          defer(() -> s3AttContentStore.buildS3AttachmentVersionKey(att)));
      s3AttContentStore.saveContent(att.loadContent());
      return true;
    }
    return false;
  }

  // let's rebuild the archive without content blobs and delete the content
  public void cleanup(XWikiAttachment att) throws XWikiException {
    LOGGER.trace("[{}] cleanup {}",
        defer(() -> att.getWikiReference().getName()),
        defer(() -> serialize(att.getAttachmentReference())));
    hibAttContentStore.executeWrite(att.getWikiReference(), true, session -> {
      try {
        var archive = att.loadArchive();
        archive.rebuildArchive(false);
        getAttachmentVersioningStore().saveArchive(archive, false);
        hibAttContentStore.deleteContent(att);
      } catch (AttachmentContentStoreException e) {
        throw new XWikiException(0, 0, "Failed deleting content from fallback store for " +
            serialize(att.getAttachmentReference()), e);
      }
      return null;
    });
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
