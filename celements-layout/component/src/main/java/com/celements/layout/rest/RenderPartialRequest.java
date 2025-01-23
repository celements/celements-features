package com.celements.layout.rest;

public class RenderPartialRequest {

  public String contextDocSpace;
  public String contextDocName;
  public String layoutSpace;
  public String startNodeName;

  @Override
  public String toString() {
    return "RenderPartialRequest ["
        + "contextDocSpace=" + contextDocSpace + ", "
        + "contextDocName=" + contextDocName + ", "
        + "layoutSpace=" + layoutSpace + ", "
        + "startNodeName=" + startNodeName + "]";
  }

}
