/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xpn.xwiki.plugin.lucene.searcherProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Singleton;

import org.apache.lucene.search.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.component.annotation.Component;

@Component
@Singleton
public class SearcherProviderManager implements ISearcherProviderRole {

  private static final Logger LOGGER = LoggerFactory.getLogger(SearcherProviderManager.class);

  private Set<SearcherProvider> allSearcherProviderSet = Collections.newSetFromMap(
      new ConcurrentHashMap<SearcherProvider, Boolean>());

  @Override
  public void closeAllForCurrentThread() {
    int numSearchProviders = getAllSearcherProviders().size();
    LOGGER.debug("closeAllForCurrentThread start in manager [{}]: remaining [{}] searchProviders.",
        System.identityHashCode(this), numSearchProviders);
    List<SearcherProvider> searcherProviderToRemove = new ArrayList<SearcherProvider>(
        numSearchProviders);
    for (SearcherProvider searcherProvider : getAllSearcherProviders()) {
      try {
        LOGGER.trace("before cleanup for searchProvider [" + System.identityHashCode(
            searcherProvider) + "], isIdle [" + searcherProvider.isIdle() + "].");
        searcherProvider.disconnect();
        searcherProvider.cleanUpAllSearchResultsForThread();
        LOGGER.trace("after cleanup for searchProvider [" + System.identityHashCode(
            searcherProvider) + "], isIdle [" + searcherProvider.isIdle() + "].");
      } catch (IOException exp) {
        LOGGER.error("Failed to disconnect searcherProvider from thread.", exp);
      }
      if (searcherProvider.isClosed()) {
        searcherProviderToRemove.add(searcherProvider);
      }
    }
    getAllSearcherProviders().removeAll(searcherProviderToRemove);
    LOGGER.info("closeAllForCurrentThread finish in manager [{}]: remaining [{}] searchProviders."
        + " removed [{}].", System.identityHashCode(this), getAllSearcherProviders().size(),
        searcherProviderToRemove.size());
  }

  Set<SearcherProvider> getAllSearcherProviders() {
    return allSearcherProviderSet;
  }

  @Override
  synchronized public SearcherProvider createSearchProvider(Searcher[] theSearchers) {
    /*
     * createSearchProvider must be synchronized to ensure that all threads will see a fully
     * initialized SearcherProvider object
     */
    SearcherProvider newSearcherProvider = new SearcherProvider(theSearchers);
    getAllSearcherProviders().add(newSearcherProvider);
    LOGGER.debug("createSearchProvider in manager [{}]: returning new SearchProvider and added to"
        + " list [{}].", System.identityHashCode(this), getAllSearcherProviders().size());
    return newSearcherProvider;
  }

}
