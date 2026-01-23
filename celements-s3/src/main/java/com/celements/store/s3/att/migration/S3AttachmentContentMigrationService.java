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

import com.celements.model.access.IModelAccessFacade;
import com.celements.model.util.ModelUtils;
import com.celements.query.IQueryExecutionServiceRole;
import com.celements.store.s3.att.S3AttachmentContentStore;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.store.AttachmentContentStore.AttachmentContentStoreException;

import one.util.streamex.StreamEx;

@Service
@Lazy
public class S3AttachmentContentMigrationService {

  static final Logger LOGGER = LoggerFactory.getLogger(S3AttachmentContentMigrationService.class);

  private final IQueryExecutionServiceRole queryExecutor;
  private final S3AttachmentContentStore s3AttStore;
  private final IModelAccessFacade modelAccess;
  private final ModelUtils modelUtils;

  @Inject
  public S3AttachmentContentMigrationService(
      IQueryExecutionServiceRole queryExecutor,
      S3AttachmentContentStore s3AttStore,
      IModelAccessFacade modelAccess,
      ModelUtils modelUtils) {
    this.queryExecutor = queryExecutor;
    this.s3AttStore = s3AttStore;
    this.modelAccess = modelAccess;
    this.modelUtils = modelUtils;
  }

  private static String getSqlAttachmentsWithContent() {
    return "SELECT DISTINCT d.XWD_FULLNAME, a.XWA_FILENAME "
        + "FROM xwikidoc d "
        + "JOIN xwikiattachment a ON d.XWD_ID = a.XWA_DOC_ID "
        + "JOIN xwikiattachment_content c ON a.XWA_ID = c.XWA_ID";
  }

  public void migrate(WikiReference wiki) throws XWikiException, AttachmentContentStoreException {
    var result = queryExecutor.executeReadSql(getSqlAttachmentsWithContent());
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

  public int migrate(XWikiAttachment att) throws XWikiException, AttachmentContentStoreException {
    var archive = att.loadArchive();
    var versions = StreamEx.of(archive.getVersions()).append(att.getRCSVersion());
    var count = 0;
    for (var v : versions.distinct()) {
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
}
