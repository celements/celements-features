package com.celements.mandatory;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.model.reference.DocumentReference;

import com.celements.model.classes.ClassDefinition;
import com.celements.model.object.xwiki.XWikiObjectEditor;
import com.celements.model.reference.RefBuilder;
import com.celements.web.classes.oldcore.XWikiGroupsClass;
import com.xpn.xwiki.XWikiConstant;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;

@Component("celements.mandatory.XWikiAdminUser")
public class XWikiAdminUser extends AbstractMandatoryDocument {

  public static final String ADMIN_DOC_NAME = "Admin";

  private static final Logger LOGGER = LoggerFactory.getLogger(XWikiAdminUser.class);

  @Requirement(XWikiGroupsClass.CLASS_DEF_HINT)
  private ClassDefinition groupsClass;

  @Override
  public String getName() {
    return "XWikiAdminUser";
  }

  @Override
  public List<String> dependsOnMandatoryDocuments() {
    return List.of("celements.MandatoryGroups");
  }

  @Override
  protected DocumentReference getDocRef() {
    return new RefBuilder().with(modelContext.getWikiRef())
        .space(XWikiConstant.XWIKI_SPACE)
        .doc("XWikiAdminGroup")
        .build(DocumentReference.class);
  }

  @Override
  protected boolean isEnabledByDefault() {
    return false;
  }

  @Override
  protected boolean skip() {
    return !modelUtils.isMainWiki(modelContext.getWikiRef());
  }

  @Override
  protected boolean checkDocuments(XWikiDocument doc) throws XWikiException {
    return false;
  }

  @Override
  protected boolean checkDocumentsMain(XWikiDocument doc) throws XWikiException {
    return addAdminUserToAdminGroup(doc);
  }

  boolean addAdminUserToAdminGroup(XWikiDocument groupDoc) {
    var adminUserDocRef = getAdminUserDocRef();
    var editor = XWikiObjectEditor.on(groupDoc)
        .filter(groupsClass)
        .filter(XWikiGroupsClass.FIELD_MEMBER, adminUserDocRef);
    if (editor.fetch().exists()) {
      return false;
    }
    editor.createFirst();
    LOGGER.info("added [{}] to [{}]", adminUserDocRef, groupDoc.getDocRef());
    return true;
  }

  private DocumentReference getAdminUserDocRef() {
    return new RefBuilder()
        .with(modelContext.getWikiRef())
        .space(XWikiConstant.XWIKI_SPACE)
        .doc(ADMIN_DOC_NAME)
        .build(DocumentReference.class);
  }

  @Override
  public Logger getLogger() {
    return LOGGER;
  }

}
