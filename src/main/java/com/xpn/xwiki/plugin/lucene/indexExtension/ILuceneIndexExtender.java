package com.xpn.xwiki.plugin.lucene.indexExtension;

import java.util.Collection;

import org.apache.lucene.document.Fieldable;
import org.xwiki.component.annotation.ComponentRole;

import com.xpn.xwiki.plugin.lucene.AbstractIndexData;

@ComponentRole
public interface ILuceneIndexExtender {

  /**
   * @param data
   *          about to be indexed
   * @return true if the provided data is eligible and should be extended by this extender
   */
  public boolean isEligibleIndexData(AbstractIndexData data);

  /**
   * @param data
   *          about to be indexed
   * @return the fields to be added to the lucene document
   */
  public Collection<Fieldable> getExtensionFields(AbstractIndexData data);

}
