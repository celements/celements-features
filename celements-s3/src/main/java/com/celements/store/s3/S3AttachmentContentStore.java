package com.celements.store.s3;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.celements.servlet.NodeConfig.NodeIdentity;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiAttachmentContent;
import com.xpn.xwiki.store.AttachmentContentStore;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Component
@Named(S3AttachmentContentStore.STORE_NAME)
@Lazy
public class S3AttachmentContentStore implements AttachmentContentStore {

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
    this.s3Client = s3Client.orElseThrow(IllegalStateException::new);
    this.s3BucketFilebase = s3BucketFilebase.orElseThrow(IllegalStateException::new);
  }

  @Override
  public String getStoreName() {
    return STORE_NAME;
  }

  public String buildS3Key(XWikiAttachment attachment) {
    var doc = attachment.getDoc();
    var wiki = doc.getDocumentReference().getWikiReference();
    return String.join("/",
        "attcontent", "v1", // base path
        "app", nodeIdentity.appName(), // allow bucket multi-tenancy by app name
        "wiki", wiki.getName(), // identify wiki
        "doc", Long.toString(doc.getId()), // identify document
        "att", Long.toString(attachment.getId()), // identify attachment
        "ver", attachment.getVersion()); // identify attachment version
  }

  public boolean hasContent(XWikiAttachment attachment) throws AttachmentContentStoreException {
    try {
      s3Client.headObject(builder -> builder
          .bucket(s3BucketFilebase)
          .key(buildS3Key(attachment)));
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    } catch (Exception e) {
      throw new AttachmentContentStoreException("Failed checking attachment", e);
    }
  }

  @Override
  public void saveContent(XWikiAttachmentContent content) throws AttachmentContentStoreException {
    try {
      var attachment = content.getAttachment();
      try (var data = content.getContentInputStream()) {
        s3Client.putObject(builder -> builder
            .bucket(s3BucketFilebase)
            .key(buildS3Key(attachment))
            .contentLength(content.getSize())
            .contentType(attachment.getMimeType()),
            RequestBody.fromInputStream(data, content.getSize()));
      }
    } catch (Exception e) {
      throw new AttachmentContentStoreException("Failed saving attachment", e);
    }
  }

  @Override
  public void loadContent(XWikiAttachmentContent content) throws AttachmentContentStoreException {
    try {
      var attachment = content.getAttachment();
      try (var data = s3Client.getObject(builder -> builder
          .bucket(s3BucketFilebase)
          .key(buildS3Key(attachment)))) {
        content.setContent(data);
      }
    } catch (Exception e) {
      throw new AttachmentContentStoreException("Failed loading attachment", e);
    }
  }

  @Override
  public void deleteContent(XWikiAttachmentContent content) throws AttachmentContentStoreException {
    try {
      var attachment = content.getAttachment();
      s3Client.deleteObject(builder -> builder
          .bucket(s3BucketFilebase)
          .key(buildS3Key(attachment)));
    } catch (Exception e) {
      throw new AttachmentContentStoreException("Failed deleting attachment", e);
    }
  }

}
