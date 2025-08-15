package com.celements.token;

import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.xwiki.model.reference.ClassReference;
import org.xwiki.model.reference.DocumentReference;

import com.xpn.xwiki.doc.XWikiDocument;

public interface TokenService {

  @NotNull
  Optional<String> addNewTokenToDocument(@NotNull DocumentReference tokenDocRef, int minutesValid);

  @NotNull
  Optional<String> addNewTokenToDocument(@NotNull XWikiDocument tokenDoc, int minutesValid);

  @NotNull
  ClassReference getTokenClassRef();

}
