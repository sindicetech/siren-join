/**
 * Copyright (c) 2015, SIREn Solutions. All Rights Reserved.
 *
 * This file is part of the SIREn project.
 *
 * SIREn is a free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * SIREn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.sirensolutions.siren.join.index.query;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.BaseFilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;

import java.io.IOException;

/**
 * A filter for a field based on terms coming from another set of documents.
 */
public class FilterJoinBuilder extends BaseFilterBuilder {

  private final String name;
  private String[] indices;
  private String[] types;
  private String routing;
  private String path;
  private QueryBuilder query;
  private String orderBy;
  private Integer maxTermsPerShard;

  private Boolean cache;
  private String cacheKey;
  private String filterName;

  public static final String NAME = "filterjoin";

  public FilterJoinBuilder(String name) {
    this.name = name;
  }

  /**
   * Sets the index names to lookup the terms from.
   */
  public FilterJoinBuilder indices(String... indices) {
    this.indices = indices;
    return this;
  }

  /**
   * Sets the index types to lookup the terms from.
   */
  public FilterJoinBuilder types(String... types) {
    this.types = types;
    return this;
  }

  /**
   * Sets the path within the document to lookup the terms from.
   */
  public FilterJoinBuilder path(String path) {
    this.path = path;
    return this;
  }

  /**
   * Sets the node routing used to control the shards the lookup request is executed on
   */
  public FilterJoinBuilder routing(String lookupRouting) {
    this.routing = lookupRouting;
    return this;
  }

  /**
   * Sets the query used to lookup terms with
   */
  public FilterJoinBuilder query(QueryBuilder query) {
    this.query = query;
    return this;
  }

  /**
   * Sets the ordering used to lookup terms
   */
  public FilterJoinBuilder orderBy(String orderBy) {
    this.orderBy = orderBy;
    return this;
  }

  /**
   * Sets the maximum number of terms per shard to lookup
   */
  public FilterJoinBuilder maxTermsPerShard(int maxTermsPerShard) {
    this.maxTermsPerShard = maxTermsPerShard;
    return this;
  }


  /**
   * Sets the filter name for the filter that can be used when searching for matched_filters per hit.
   */
  public FilterJoinBuilder filterName(String filterName) {
    this.filterName = filterName;
    return this;
  }

  /**
   * Sets if the resulting filter should be cached or not
   */
  public FilterJoinBuilder cache(boolean cache) {
    this.cache = cache;
    return this;
  }

  /**
   * Sets the filter cache key
   */
  public FilterJoinBuilder cacheKey(String cacheKey) {
    this.cacheKey = cacheKey;
    return this;
  }

  @Override
  public void doXContent(XContentBuilder builder, Params params) throws IOException {
    builder.startObject(FilterJoinBuilder.NAME);

    builder.startObject(name);
    if (indices != null) {
      builder.field("indices", indices);
    }
    if (types != null) {
      builder.field("types", types);
    }
    if (routing != null) {
      builder.field("routing", routing);
    }
    builder.field("path", path);
    builder.field("query", query);
    if (orderBy != null) {
      builder.field("orderBy", orderBy);
    }
    if (maxTermsPerShard != null) {
      builder.field("maxTermsPerShard", maxTermsPerShard);
    }
    builder.endObject();

    if (filterName != null) {
      builder.field("_name", filterName);
    }
    if (cache != null) {
      builder.field("_cache", cache);
    }
    if (cacheKey != null) {
      builder.field("_cache_key", cacheKey);
    }

    builder.endObject();
  }
}