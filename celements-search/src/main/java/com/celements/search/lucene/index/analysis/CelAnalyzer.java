package com.celements.search.lucene.index.analysis;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

public interface CelAnalyzer {

  @NotNull
  String filterToken(@Nullable String token);

}
