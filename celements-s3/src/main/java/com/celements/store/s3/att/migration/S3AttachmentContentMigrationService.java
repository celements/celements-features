package com.celements.store.s3.att.migration;

import static com.celements.logging.LogUtils.*;

import java.util.List;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xwiki.model.reference.DocumentReference;
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
    return "SELECT d.XWD_FULLNAME "
        + "FROM xwikidoc d "
        + "JOIN xwikiattachment a ON d.XWD_ID = a.XWA_DOC_ID "
        + "JOIN xwikiattachment_content c ON a.XWA_ID = c.XWA_ID";
  }

  public void migrate(WikiReference wiki) throws XWikiException {
    var result = queryExecutor.executeReadSql(getSqlAttachmentsWithContent());
    LOGGER.info("[{}] migrating {} attachments to S3 store", wiki.getName(), result.size());
    for (List<String> row : result) {
      var docRef = modelUtils.resolveRef(row.get(0), DocumentReference.class, wiki);
      try {
        migrate(docRef);
      } catch (Exception exc) {
        LOGGER.warn("[{}] failed migrating document: {}", wiki.getName(), docRef, exc);
      }
    }
  }

  public void migrate(DocumentReference docRef) {
    for (var att : modelAccess.getOrCreateDocument(docRef).getAttachmentList()) {
      try {
        migrate(att);
      } catch (XWikiException | AttachmentContentStoreException exc) {
        LOGGER.warn("[{}] failed migrating: {}",
            att.getDoc().getDocumentReference().getWikiReference().getName(), att, exc);
      }
    }
  }

  public void migrate(XWikiAttachment att)
      throws XWikiException, AttachmentContentStoreException {
    var archive = att.loadArchive();
    var versions = StreamEx.of(archive.getVersions()).append(att.getRCSVersion());
    for (var v : versions.distinct()) {
      pushToS3(archive.getRevision(v));
    }
  }

  private void pushToS3(XWikiAttachment att)
      throws XWikiException, AttachmentContentStoreException {
    if (!s3AttStore.hasContent(att)) {
      LOGGER.debug("[{}] pushToS3 {}",
          defer(() -> att.getDoc().getDocumentReference().getWikiReference().getName()),
          defer(() -> s3AttStore.buildS3AttachmentVersionKey(att)));
      s3AttStore.saveContent(att.loadContent());
    }
  }
}
