package com.celements.auth.user;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;
import javax.inject.Named;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;

import com.celements.model.access.IModelAccessFacade;
import com.celements.model.access.exception.DocumentNotExistsException;
import com.celements.model.classes.ClassDefinition;
import com.celements.model.classes.fields.ClassField;
import com.celements.model.object.xwiki.XWikiObjectFetcher;
import com.celements.model.util.ModelUtils;
import com.celements.web.classes.oldcore.XWikiUsersClass;
import com.celements.web.classes.oldcore.XWikiUsersClass.Type;
import com.google.common.base.Strings;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.user.api.XWikiUser;

@NotThreadSafe
@Component(CelementsUser.NAME)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class CelementsUser implements User {

  public static final String NAME = "CelementsUser";

  private final ClassDefinition usersClass;
  private final IModelAccessFacade modelAccess;
  private final ModelUtils modelUtils;

  /**
   * Used to convert a Document Reference to a username to a string. Note that we must be careful
   * not to include the
   * wiki name as part of the serialized name since user names are saved in the database (for
   * example as the document
   * author when you create a new document) and we're only supposed to save the wiki part when the
   * user is from
   * another wiki. This should probably be fixed in the future though but it requires changing
   * existing code that
   * depend on this behavior.
   */
  private final EntityReferenceSerializer<String> compactWikiEntityReferenceSerializer;

  private XWikiDocument userDoc;

  @Inject
  public CelementsUser(IModelAccessFacade modelAccess, ModelUtils modelUtils,
      @Named(XWikiUsersClass.CLASS_DEF_HINT) ClassDefinition usersClass,
      @Named("compactwiki") EntityReferenceSerializer<String> compactWikiEntityReferenceSerializer) {
    this.modelAccess = modelAccess;
    this.modelUtils = modelUtils;
    this.usersClass = usersClass;
    this.compactWikiEntityReferenceSerializer = compactWikiEntityReferenceSerializer;
  }

  @Override
  public void initialize(DocumentReference userDocRef) throws UserInstantiationException {
    try {
      userDoc = modelAccess.getDocument(userDocRef);
      if (!getUserObjectFetcher().exists()) {
        throw new UserInstantiationException("No user object on doc: " + userDocRef);
      }
    } catch (DocumentNotExistsException exc) {
      throw new UserInstantiationException(exc);
    }
  }

  @Override
  public DocumentReference getDocRef() {
    return userDoc.getDocumentReference();
  }

  @Override
  public XWikiDocument getDocument() {
    return userDoc;
  }

  @Override
  public boolean isGlobal() {
    return modelUtils.isMainWiki(getDocRef().getWikiReference());
  }

  @Override
  public XWikiUser asXWikiUser() {
    return new XWikiUser(this.compactWikiEntityReferenceSerializer.serialize(getDocRef()),
        isGlobal());
  }

  /**
   * @deprecated instead use {@link #email()}
   * @since 5.9
   */
  @Deprecated(since = "5.9", forRemoval = true)
  @Override
  public com.google.common.base.Optional<String> getEmail() {
    return com.google.common.base.Optional.fromJavaUtil(email());
  }

  @Override
  public Optional<String> email() {
    return getUserFieldValue(XWikiUsersClass.FIELD_EMAIL);
  }

  /**
   * @deprecated instead use {@link #firstName()}
   * @since 5.9
   */
  @Deprecated(since = "5.9", forRemoval = true)
  @Override
  public com.google.common.base.Optional<String> getFirstName() {
    return com.google.common.base.Optional.fromJavaUtil(firstName());
  }

  @Override
  public Optional<String> firstName() {
    return getUserFieldValue(XWikiUsersClass.FIELD_FIRST_NAME);
  }

  /**
   * @deprecated instead use {@link #lastName()}
   * @since 5.9
   */
  @Deprecated(since = "5.9", forRemoval = true)
  @Override
  public com.google.common.base.Optional<String> getLastName() {
    return com.google.common.base.Optional.fromJavaUtil(lastName());
  }

  @Override
  public Optional<String> lastName() {
    return getUserFieldValue(XWikiUsersClass.FIELD_LAST_NAME);
  }

  /**
   * @deprecated instead use {@link #prettyName()}
   * @since 5.9
   */
  @Deprecated(since = "5.9", forRemoval = true)
  @Override
  public com.google.common.base.Optional<String> getPrettyName() {
    return com.google.common.base.Optional.fromJavaUtil(prettyName());
  }

  @Override
  public Optional<String> prettyName() {
    String prettyName = firstName().orElse("") + " " + lastName().orElse("");
    return Optional.ofNullable(Strings.emptyToNull(prettyName.trim()));
  }

  @Override
  public Type getType() {
    return getUserFieldValue(XWikiUsersClass.FIELD_TYPE).orElse(Type.Simple);
  }

  @Override
  public boolean isSuspended() {
    return getUserFieldValue(XWikiUsersClass.FIELD_SUSPENDED).orElse(false);
  }

  @Override
  public boolean isActive() {
    return getUserFieldValue(XWikiUsersClass.FIELD_ACTIVE).orElse(false);
  }

  /**
   * @deprecated instead use {@link #getAdminLang()}
   * @since 5.9
   */
  @Deprecated(since = "5.9", forRemoval = true)
  @Override
  public com.google.common.base.Optional<String> getAdminLanguage() {
    return com.google.common.base.Optional.fromJavaUtil(getAdminLang());
  }

  @Override
  public Optional<String> getAdminLang() {
    return getUserFieldValue(XWikiUsersClass.FIELD_ADMIN_LANG);
  }

  private <T> Optional<T> getUserFieldValue(ClassField<T> field) {
    return getUserObjectFetcher().fetchField(field).stream().findFirst();
  }

  private XWikiObjectFetcher getUserObjectFetcher() {
    return XWikiObjectFetcher.on(userDoc).filter(usersClass);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getDocRef());
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof User) {
      User other = (User) obj;
      return Objects.equals(this.getDocRef(), other.getDocRef());
    }
    return false;
  }

  @Override
  public String toString() {
    return "CelementsUser [userDocRef=" + getDocRef() + "]";
  }

}
