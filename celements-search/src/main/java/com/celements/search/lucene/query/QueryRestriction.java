package com.celements.search.lucene.query;

import static com.celements.search.lucene.LuceneUtils.*;
import static com.celements.search.lucene.query.QueryRestriction.QueryMode.*;
import static com.google.common.base.MoreObjects.*;
import static com.google.common.base.Strings.*;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.lucene.queryParser.QueryParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.celements.search.lucene.index.analysis.CelAnalyzer;
import com.google.common.base.Optional;

public class QueryRestriction implements IQueryRestriction {

  public enum QueryMode {
    DEFAULT, EXACT, TOKENIZED, TOKENIZED_OR;
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(QueryRestriction.class);

  private static final Pattern TOKEN_PATTERN = Pattern.compile("( ?\"[^\"]*\" ?)|[^ ]+");

  private boolean negate = false;
  private String specifier = null;
  private String query = null;
  private QueryMode mode = TOKENIZED;
  private Float fuzzy = null;
  private Integer proximity = null;
  private Float boost = null;
  private CelAnalyzer analyzer = null;

  public QueryRestriction(String specifier, String query) {
    setSpecifier(specifier);
    setQuery(query);
  }

  /**
   * @deprecated since 6.0 instead use {@link #setMode(QueryMode)}
   */
  @Deprecated
  public QueryRestriction(String specifier, String query, boolean tokenizeQuery) {
    this(specifier, query);
    setTokenizeQuery(tokenizeQuery);
  }

  public String getSpecifier() {
    return specifier;
  }

  public String getQuery() {
    return query;
  }

  @Override
  public boolean getNegate() {
    return negate;
  }

  @Override
  public QueryRestriction setNegate(boolean negate) {
    this.negate = negate;
    return this;
  }

  public QueryRestriction setSpecifier(String specifier) {
    this.specifier = nullToEmpty(specifier).trim();
    return this;
  }

  public QueryRestriction setQuery(String query) {
    this.query = nullToEmpty(query).trim();
    return this;
  }

  public QueryRestriction setMode(@Nullable QueryMode mode) {
    this.mode = firstNonNull(mode, DEFAULT);
    return this;
  }

  /**
   * Uses required/AND for multiple keywords when true, e.g. (+keyword1 +keyword2),
   * else (keyword1 keyword2)
   *
   * @param tokenizeQuery
   * @return
   *
   * @deprecated since 6.0 instead use {@link #setMode(QueryMode)}
   */
  @Deprecated
  public QueryRestriction setTokenizeQuery(boolean tokenizeQuery) {
    return setMode(tokenizeQuery ? TOKENIZED : DEFAULT);
  }

  @Override
  public Optional<Float> getFuzzy() {
    return Optional.fromNullable(fuzzy);
  }

  @Override
  public QueryRestriction setFuzzy(@Nullable Float fuzzy) {
    this.fuzzy = fuzzy;
    return this;
  }

  /**
   * Use fuzzy search to find similar words.
   *
   * @param fuzzy
   *          Allowed distance from 0 to 1 where the closer to 1 the parameter is,
   *          the higher similarity the match needs to have.
   */
  public QueryRestriction setFuzzy(String fuzzy) {
    try {
      setFuzzy(Float.parseFloat(fuzzy));
    } catch (NumberFormatException nfe) {
      LOGGER.error("Exception parsing float of '{}'", fuzzy, nfe);
    }
    return this;
  }

  public QueryRestriction setFuzzy(float fuzzy) {
    return setFuzzy((Float) fuzzy);
  }

  public QueryRestriction setFuzzy() {
    return setFuzzy(-1f); // use Lucene's default (which is 0.5)
  }

  /**
   * The term's words have to be in the given proximity e.g. at most 8 words apart.
   *
   * @param proximity
   *          How many words apart can the words be.
   */
  public QueryRestriction setProximity(String proximity) {
    try {
      setProximity(Integer.parseInt(proximity));
    } catch (NumberFormatException nfe) {
      LOGGER.error("Exception parsing float of '{}'", proximity, nfe);
    }
    return this;
  }

  public QueryRestriction setProximity(int proximity) {
    this.proximity = proximity;
    return this;
  }

  /**
   * A boost factor for the term. The higher the boost factor, the more relevant the term.
   * The boost factor has to be positive, can be < 1 though. The default is 1.
   *
   * @param boost
   *          The factor to boost the term.
   * @return
   */
  public QueryRestriction setBoost(String boost) {
    try {
      setBoost(Float.parseFloat(boost));
    } catch (NumberFormatException nfe) {
      LOGGER.error("Exception parsing float of '{}'", boost, nfe);
    }
    return this;
  }

  public QueryRestriction setBoost(float boost) {
    this.boost = boost;
    return this;
  }

  public QueryRestriction setAnalyzer(CelAnalyzer analyzer) {
    this.analyzer = analyzer;
    return this;
  }

  /**
   * @deprecated instead use {@link #getQueryString()}
   * @return
   */
  @Deprecated
  public String getRestriction() {
    return getQueryString();
  }

  @Override
  public String getQueryString() {
    String ret = "";
    if (!specifier.isEmpty()) {
      ret = getQueryValue();
      if (!ret.isEmpty()) {
        DecimalFormat formater = getDecimalFormater();
        ret = makeRestrictionFuzzy(ret, formater);
        if ((proximity != null) && (proximity > 1)) {
          ret = "\"" + ret + "\"~" + formater.format(proximity);
        }
        ret = specifier + ":(" + ret + ")";
        if ((boost != null) && (boost > 0)) {
          ret += "^" + formater.format(boost);
        }
        if (negate) {
          ret = "NOT " + ret;
        }
      }
    }
    return ret;
  }

  private String getQueryValue() {
    switch (mode) {
      case TOKENIZED:
        return getTokenizedQuery(true);
      case TOKENIZED_OR:
        return getTokenizedQuery(false);
      case EXACT:
        return exactify(filterToken(query));
      default:
        return filterToken(query);
    }
  }

  private String getTokenizedQuery(boolean and) {
    Matcher m = TOKEN_PATTERN.matcher(query);
    StringBuilder tokenizedQuery = new StringBuilder();
    while (m.find()) {
      String token = filterToken(m.group(0).trim());
      if (!token.isEmpty() && !token.matches("[\"+-]")) {
        tokenizedQuery.append(" ");
        token = QueryParser.escape(token);
        token = token.replaceAll("^(\\\\([+-]))?(\\\\(\"))?(.*?)(\\\\(\"))?$", "$2$4$5$7");
        token = token.replaceAll("^(.+)\\\\([\\?\\*].*)$", "$1$2");
        if (and && !token.matches("^[+-].*")) {
          tokenizedQuery.append("+");
        }
        tokenizedQuery.append(token);
        if (!token.matches("^.*[\\*\\\"]$") && ((proximity == null) || (proximity <= 1))) {
          tokenizedQuery.append("*");
        }
      }
    }
    return tokenizedQuery.toString().trim();
  }

  String filterToken(String token) {
    if (analyzer != null) {
      return analyzer.filterToken(token);
    } else {
      return token;
    }
  }

  DecimalFormat getDecimalFormater() {
    DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMAN);
    symbols.setDecimalSeparator('.');
    return new DecimalFormat("#.###", symbols);
  }

  String makeRestrictionFuzzy(String queryString, DecimalFormat formater) {
    if (fuzzy != null) {
      String fuzzyStr = "~";
      if ((fuzzy >= 0) && (fuzzy <= 1)) {
        fuzzyStr += formater.format(fuzzy);
      }
      StringBuilder finalQuery = new StringBuilder();
      for (String term : queryString.split(" ")) {
        if (finalQuery.length() > 0) {
          finalQuery.append(" AND ");
        }
        if (term.endsWith("*")) {
          finalQuery.append("(" + term.replaceAll("\\+?(.*)", "$1") + " OR " + term.replaceAll(
              "\\+?(.*)\\*$", "$1" + fuzzyStr) + ")");
        } else {
          finalQuery.append(term).append(fuzzyStr);
        }
      }
      return finalQuery.toString();
    } else {
      return queryString;
    }
  }

  @Override
  public QueryRestriction copy() {
    QueryRestriction copy = new QueryRestriction(specifier, query);
    copy.mode = mode;
    copy.fuzzy = fuzzy;
    copy.proximity = proximity;
    copy.boost = boost;
    copy.negate = negate;
    copy.analyzer = analyzer;
    return copy;
  }

  @Override
  public boolean isEmpty() {
    return getQueryString().isBlank();
  }

  @Override
  public int hashCode() {
    return Objects.hash(boost, fuzzy, negate, proximity, query, specifier, mode, analyzer);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof QueryRestriction) {
      QueryRestriction other = (QueryRestriction) obj;
      return Objects.equals(specifier, other.specifier)
          && Objects.equals(query, other.query)
          && Objects.equals(mode, other.mode)
          && Objects.equals(boost, other.boost)
          && Objects.equals(fuzzy, other.fuzzy)
          && Objects.equals(negate, other.negate)
          && Objects.equals(proximity, other.proximity)
          && Objects.equals(analyzer, other.analyzer);
    } else {
      return false;
    }
  }

  @Override
  public String toString() {
    return "QueryRestriction [negate=" + negate + ", specifier=" + specifier + ", query=" + query
        + ", mode=" + mode + ", fuzzy=" + fuzzy + ", proximity=" + proximity + ", boost=" + boost
        + ", analyzer=" + analyzer + ", queryString=" + getQueryString() + "]";
  }

}
