package com.sindicetech.kb.filterjoin.action.coordinate;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.ShardSearchFailure;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.facet.Facets;
import org.elasticsearch.search.suggest.Suggest;

import java.io.IOException;

/**
 * A search response for the coordinate search action. It is a decorator around the {@link SearchResponse}
 * which injects filter join execution metadata into the response.
 * <br>
 * We use a decorator pattern instead of a subclass extension because there are many hard-coded
 * instantiations of {@link SearchResponse}, e.g., in
 * {@link org.elasticsearch.action.search.type.TransportSearchTypeAction}, and it would have required to
 * subclass all of them to instantiate a {@link CoordinateSearchResponse} instead.
 */
public class CoordinateSearchResponse extends SearchResponse {

  private SearchResponse searchResponse;
  private CoordinateSearchMetadata coordinateSearchMetadata;

  public CoordinateSearchResponse(SearchResponse response, CoordinateSearchMetadata metadata) {
    this.searchResponse = response;
    this.coordinateSearchMetadata = metadata;
  }

  /**
   * Constructor for {@link CoordinateSearchAction#newResponse()} and
   * deserialization in {@link com.sindicetech.kb.filterjoin.action.coordinate.CoordinateMultiSearchResponse.Item}.
   */
  CoordinateSearchResponse() {}

  @Override
  public RestStatus status() {
    return searchResponse.status();
  }

  @Override
  public SearchHits getHits() {
    return searchResponse.getHits();
  }

  @Override
  public Facets getFacets() {
    return searchResponse.getFacets();
  }

  @Override
  public Aggregations getAggregations() {
    return searchResponse.getAggregations();
  }

  @Override
  public Suggest getSuggest() {
    return searchResponse.getSuggest();
  }

  @Override
  public boolean isTimedOut() {
    return searchResponse.isTimedOut();
  }

  @Override
  public Boolean isTerminatedEarly() {
    return searchResponse.isTerminatedEarly();
  }

  @Override
  public TimeValue getTook() {
    return searchResponse.getTook();
  }

  @Override
  public long getTookInMillis() {
    return searchResponse.getTookInMillis();
  }

  @Override
  public int getTotalShards() {
    return searchResponse.getTotalShards();
  }

  @Override
  public int getSuccessfulShards() {
    return searchResponse.getSuccessfulShards();
  }

  @Override
  public int getFailedShards() {
    return searchResponse.getFailedShards();
  }

  @Override
  public ShardSearchFailure[] getShardFailures() {
    return searchResponse.getShardFailures();
  }

  @Override
  public String getScrollId() {
    return searchResponse.getScrollId();
  }

  @Override
  public void scrollId(String scrollId) {
    searchResponse.scrollId(scrollId);
  }

  @Override
  public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
    coordinateSearchMetadata.toXContent(builder);
    return this.searchResponse.toXContent(builder, params);
  }

  @Override
  public void readFrom(StreamInput in) throws IOException {
    this.searchResponse = new SearchResponse();
    this.searchResponse.readFrom(in);

    this.coordinateSearchMetadata = new CoordinateSearchMetadata();
    this.coordinateSearchMetadata.readFrom(in);
  }

  @Override
  public void writeTo(StreamOutput out) throws IOException {
    this.searchResponse.writeTo(out);
    this.coordinateSearchMetadata.writeTo(out);
  }

}
