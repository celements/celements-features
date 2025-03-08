package com.celements.layout.rest;

public class RenderPartialRequest {

  private final String contextDocSpace;
  private final String contextDocName;
  private final String layoutSpace;
  private final String startNodeName;
  private final String language;

  public RenderPartialRequest(String contextDocSpace, String contextDocName, String layoutSpace,
      String startNodeName, String language) {
    this.contextDocSpace = contextDocSpace;
    this.contextDocName = contextDocName;
    this.layoutSpace = layoutSpace;
    this.startNodeName = startNodeName;
    this.language = language;
  }

  public String getContextDocSpace() {
    return contextDocSpace;
  }

  public String getContextDocName() {
    return contextDocName;
  }

  public String getLayoutSpace() {
    return layoutSpace;
  }

  public String getStartNodeName() {
    return startNodeName;
  }

  public String getLanguage() {
    return language;
  }

  @Override
  public String toString() {
    return "RenderPartialRequest ["
        + "contextDocSpace=" + contextDocSpace + ", "
        + "contextDocName=" + contextDocName + ", "
        + "layoutSpace=" + layoutSpace + ", "
        + "startNodeName=" + startNodeName + ", "
        + "language=" + language + "]";
  }

}
