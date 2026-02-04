package com.celements.store.s3.att;

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.celements.servlet.NodeConfig.NodeIdentity;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiAttachmentContent;
import com.xpn.xwiki.store.AttachmentContentStore;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@Named(S3AttachmentContentStore.STORE_NAME)
@Lazy
public class S3AttachmentContentStore implements AttachmentContentStore {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(S3AttachmentContentStore.class);

  public static final String STORE_NAME = "store.attachment.content.s3";

  private final NodeIdentity nodeIdentity;
  private final S3Client s3Client;
  private final String s3BucketFilebase;

  @Inject
  public S3AttachmentContentStore(
      NodeIdentity nodeIdentity,
      Optional<S3Client> s3Client,
      @Named("s3BucketFilebase") Optional<String> s3BucketFilebase) {
    this.nodeIdentity = nodeIdentity;
    this.s3Client = s3Client
        .orElseThrow(() -> new IllegalStateException("S3Client missing"));
    this.s3BucketFilebase = s3BucketFilebase
        .orElseThrow(() -> new IllegalStateException("s3BucketFilebase missing"));
  }

  @Override
  public String getStoreName() {
    return STORE_NAME;
  }

  /**
   * Builds the S3 key for the given attachment. The key structure is as follows:
   * attachment/{appName}/{wikiName}/{docId}/{attachmentId}
   */
  public String buildS3AttachmentKey(XWikiAttachment attachment) {
    var doc = attachment.getDoc();
    var wiki = doc.getDocumentReference().getWikiReference();
    return String.join("/",
        nodeIdentity.clusterName(), // allow bucket multi-tenancy by cluster name
        "attachments", // subbucket for attachments
        wiki.getName(), // identify wiki
        Long.toString(doc.getId()), // identify document
        Long.toString(attachment.getId())); // identify attachment
  }

  /**
   * Builds the S3 key for the given attachment. The key structure is as follows:
   * attachment/{appName}/{wikiName}/{docId}/{attachmentId}/{version}
   */
  public String buildS3AttachmentVersionKey(XWikiAttachment attachment) {
    return String.join("/",
        buildS3AttachmentKey(attachment),
        attachment.getVersion()); // identify attachment version
  }

  public boolean hasContent(XWikiAttachment attachment) throws AttachmentContentStoreException {
    var s3Key = buildS3AttachmentVersionKey(attachment);
    LOGGER.debug("hasContent - {} in {}", attachment, s3Key);
    try {
      s3Client.headObject(builder -> builder
          .bucket(s3BucketFilebase)
          .key(s3Key));
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    } catch (S3Exception e) {
      throw new AttachmentContentStoreException(buildS3ErrorMessage(s3Key, e), e);
    } catch (Exception e) {
      throw new AttachmentContentStoreException("Failed checking attachment", e);
    }
  }

  @Override
  public void saveContent(XWikiAttachmentContent content) throws AttachmentContentStoreException {
    var s3Key = buildS3AttachmentVersionKey(content.getAttachment());
    LOGGER.info("saveContent - {} to {}", content.getAttachment(), s3Key);
    try {
      try (var data = content.getContentInputStream()) {
        s3Client.putObject(builder -> builder
            .bucket(s3BucketFilebase)
            .key(s3Key)
            .contentLength((long) content.getSize())
            .contentType(content.getAttachment().getMimeType()),
            RequestBody.fromInputStream(data, content.getSize()));
      }
    } catch (S3Exception e) {
      throw new AttachmentContentStoreException(buildS3ErrorMessage(s3Key, e), e);
    } catch (Exception e) {
      throw new AttachmentContentStoreException("Failed saving attachment", e);
    }
  }

  @Override
  public void loadContent(XWikiAttachmentContent content) throws AttachmentContentStoreException {
    var s3Key = buildS3AttachmentVersionKey(content.getAttachment());
    try {
      try (var data = s3Client.getObject(builder -> builder
          .bucket(s3BucketFilebase)
          .key(s3Key))) {
        content.setContent(data);
      }
      LOGGER.debug("loadContent - {} from {}", content.getAttachment(), s3Key);
    } catch (NoSuchKeyException e) {
      LOGGER.info("loadContent - {} not found in {}", content.getAttachment(), s3Key);
      throw new AttachmentContentStoreException("Attachment content not found in S3: " + s3Key, e);
    } catch (S3Exception e) {
      throw new AttachmentContentStoreException(buildS3ErrorMessage(s3Key, e), e);
    } catch (Exception e) {
      throw new AttachmentContentStoreException("Failed loading attachment", e);
    }
  }

  @Override
  public void deleteContent(XWikiAttachment attachment) throws AttachmentContentStoreException {
    var s3Prefix = buildS3AttachmentKey(attachment) + "/";
    LOGGER.info("deleteContent - {} from {}", attachment, s3Prefix);
    List<ObjectIdentifier> batch = s3Client.listObjectsV2(builder -> builder
        .bucket(s3BucketFilebase)
        .prefix(s3Prefix))
        .contents()
        .stream()
        .map(s3Object -> ObjectIdentifier.builder().key(s3Object.key()).build())
        .toList();
    if (batch.isEmpty()) {
      return;
    } else if (batch.size() >= 1000) {
      throw new AttachmentContentStoreException(
          "Too many objects to delete in S3 for attachment: " + attachment, null);
    }
    s3Client.deleteObjects(builder -> builder
        .bucket(s3BucketFilebase)
        .delete(deleteBuilder -> deleteBuilder.objects(batch)));
  }

  @Override
  public void deleteContent(XWikiAttachmentContent content) throws AttachmentContentStoreException {
    var s3Key = buildS3AttachmentVersionKey(content.getAttachment());
    LOGGER.info("deleteContent - {} from {}", content.getAttachment(), s3Key);
    try {
      s3Client.deleteObject(builder -> builder
          .bucket(s3BucketFilebase)
          .key(s3Key));
    } catch (S3Exception e) {
      throw new AttachmentContentStoreException(buildS3ErrorMessage(s3Key, e), e);
    } catch (Exception e) {
      throw new AttachmentContentStoreException("Failed deleting attachment", e);
    }
  }

  private static String buildS3ErrorMessage(String s3Key, S3Exception e) {
    return String.format("S3 error for attachment (key=%s, status=%d, code=%s)",
        s3Key,
        e.statusCode(),
        e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : "n/a");
  }

}
