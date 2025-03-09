package com.celements.token;

import java.util.Optional;

import org.xwiki.model.reference.ClassReference;
import org.xwiki.model.reference.DocumentReference;

import com.xpn.xwiki.doc.XWikiDocument;

public interface TokenService {

  Optional<String> addNewTokenToDocument(DocumentReference tokenDocRef, int minutesValid);

  Optional<String> addNewTokenToDocument(XWikiDocument tokenDoc, int minutesValid);

  ClassReference getTokenClassRef();

}
