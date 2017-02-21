/**
 * Copyright (c) 2016, SIREn Solutions. All Rights Reserved.
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
package solutions.siren.join;

import org.elasticsearch.Version;
import org.elasticsearch.index.IndexModule;
import org.elasticsearch.node.MockNode;
import org.elasticsearch.node.NodeValidationException;
import org.elasticsearch.plugins.Plugin;
import solutions.siren.join.action.coordinate.CoordinateSearchRequestBuilder;
import solutions.siren.join.action.coordinate.execution.FilterJoinCache;
import solutions.siren.join.action.terms.TermsByQueryRequest;
import solutions.siren.join.index.query.FilterJoinBuilder;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.admin.cluster.node.stats.NodeStats;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.StopWatch;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.node.Node;
import solutions.siren.join.index.query.QueryBuilders;

import java.io.IOException;
import java.util.*;

import static org.elasticsearch.client.Requests.createIndexRequest;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_REPLICAS;
import static org.elasticsearch.cluster.metadata.IndexMetaData.SETTING_NUMBER_OF_SHARDS;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;

/**
 * Perform the parent/child search tests using terms query lookup.
 * This should work across multiple shards.
 */
@SuppressWarnings("unchecked")
public class FilterJoinBenchmark {

    // index settings
    public static final int NUM_SHARDS = 3;
    public static final int NUM_REPLICAS = 0;
    public static final String PARENT_INDEX = "joinparent";
    public static final String PARENT_TYPE = "p";
    public static final String CHILD_INDEX = "joinchild";
    public static final String CHILD_TYPE = "c";
    // test settings
    public static final int NUM_PARENTS = 1000000;
    public static final int NUM_CHILDREN_PER_PARENT = 5;
    public static final int BATCH_SIZE = 100;
    public static final int NUM_QUERIES = 50;
    private final Node[] nodes;
    private final Client client;
    private final Random random;

    FilterJoinBenchmark() throws NodeValidationException {
        Settings settings = Settings.builder()
                .put(FilterJoinCache.SIREN_FILTERJOIN_CACHE_ENABLED, false)
                .put("index.engine.robin.refreshInterval", "-1")
                .put("path.home", "./target/elasticsearch-benchmark/home/")
                .put("node.local", true)
                .put(SETTING_NUMBER_OF_SHARDS, NUM_SHARDS)
                .put(SETTING_NUMBER_OF_REPLICAS, NUM_REPLICAS)
                .put(IndexModule.INDEX_QUERY_CACHE_EVERYTHING_SETTING.getKey(), true)
                .build();

        this.nodes = new MockNode[2];
        this.nodes[0] = new MockNode(Settings.builder().put(settings).put("name", "node1").build(), 
                Collections.singletonList(SirenJoinPlugin.class)).start();
        this.nodes[1] = new MockNode(Settings.builder().put(settings).put("name", "node2").build(), 
                Collections.singletonList(SirenJoinPlugin.class)).start();
        this.client = nodes[0].client();
        this.random = new Random(System.currentTimeMillis());
    }

    public static void main(String[] args) throws Exception {
      FilterJoinBenchmark bench = new FilterJoinBenchmark();
      bench.waitForGreen();
      bench.setupIndex();
      bench.memStatus();

      bench.benchHasChildSingleTerm();
      bench.benchHasParentSingleTerm();
      bench.benchHasParentMatchAll();
      bench.benchHasChildMatchAll();
//        bench.benchHasParentRandomTerms();

      System.gc();
      bench.memStatus();
      bench.shutdown();
    }

    public void waitForGreen() {
      client.admin().cluster().prepareHealth().setWaitForGreenStatus().setTimeout("10s").execute().actionGet();
    }

    public void shutdown() throws IOException {
      client.close();
      nodes[0].close();
      nodes[1].close();
    }

    public void log(String msg) {
        System.out.println("--> " + msg);
    }

    public void memStatus() throws IOException {
      List<NodeStats> nodeStats = client.admin().cluster().prepareNodesStats()
        .setJvm(true).setIndices(true).setTransport(true)
        .execute().actionGet().getNodes();

      log("==== MEMORY ====");
      log("Committed heap size: [0]=" + nodeStats.get(0).getJvm().getMem().getHeapCommitted() + ", [1]=" + nodeStats.get(1).getJvm().getMem().getHeapCommitted());
      log("Used heap size: [0]=" + nodeStats.get(0).getJvm().getMem().getHeapUsed() + ", [1]=" + nodeStats.get(1).getJvm().getMem().getHeapUsed());
      log("FieldData cache size: [0]=" + nodeStats.get(0).getIndices().getFieldData().getMemorySize() + ", [1]=" + nodeStats.get(1).getIndices().getFieldData().getMemorySize());
      log("Query cache size: [0]=" + nodeStats.get(0).getIndices().getQueryCache().getMemorySize() + ", [1]=" + nodeStats.get(1).getIndices().getQueryCache().getMemorySize());
      log("");
      log("==== NETWORK ====");
      log("Transport: [0]=" + nodeStats.get(0).getTransport().toXContent(jsonBuilder(), ToXContent.EMPTY_PARAMS).string() + ", [1]=" + nodeStats.get(1).getTransport().toXContent(jsonBuilder(), ToXContent.EMPTY_PARAMS).string());
      log("");
    }

    public XContentBuilder parentSource(int id, String nameValue) throws IOException {
        return jsonBuilder().startObject().field("id", Integer.toString(id)).field("num", id).field("name", nameValue).endObject();
    }

    public XContentBuilder childSource(String id, int parent, String tag) throws IOException {
        return jsonBuilder().startObject().field("id", id).field("pid", Integer.toString(parent)).field("num", parent)
                .field("tag", tag).endObject();
    }

    public void setupIndex() {
        log("==== INDEX SETUP ====");
        try {
          client.admin().indices().create(createIndexRequest(PARENT_INDEX).mapping(PARENT_TYPE,
                  "id", "type=string,index=not_analyzed,doc_values=true",
                  "num", "type=integer,doc_values=true")).actionGet();
          client.admin().indices().create(createIndexRequest(CHILD_INDEX).mapping(CHILD_TYPE,
                  "id", "type=string,index=not_analyzed,doc_values=true",
                  "pid", "type=string,index=not_analyzed,doc_values=true",
                  "num", "type=integer,doc_values=true")).actionGet();
            Thread.sleep(5000);

            StopWatch stopWatch = new StopWatch().start();

            log("Indexing [" + NUM_PARENTS + "] parent documents into [" + PARENT_INDEX + "]");
            log("Indexing [" + (NUM_PARENTS * NUM_CHILDREN_PER_PARENT) + "] child documents into [" + CHILD_INDEX + "]");
            int ITERS = NUM_PARENTS / BATCH_SIZE;
            int i = 1;
            int counter = 0;
            for (; i <= ITERS; i++) {
                BulkRequestBuilder request = client.prepareBulk();
                for (int j = 0; j < BATCH_SIZE; j++) {
                    String parentId = Integer.toString(counter);
                    counter++;
                    request.add(Requests.indexRequest(PARENT_INDEX)
                            .type(PARENT_TYPE)
                            .id(parentId)
                            .source(parentSource(counter, "test" + counter)));

                    for (int k = 0; k < NUM_CHILDREN_PER_PARENT; k++) {
                        String childId = parentId + "_" + k;
                        request.add(Requests.indexRequest(CHILD_INDEX)
                                .type(CHILD_TYPE)
                                .id(childId)
                                .source(childSource(childId, counter, "tag" + k)));
                    }
                }

                BulkResponse response = request.execute().actionGet();
                if (response.hasFailures()) {
                    log("Index Failures...");
                }

                if (((i * BATCH_SIZE) % 10000) == 0) {
                    log("Indexed [" + (i * BATCH_SIZE) * (1 + NUM_CHILDREN_PER_PARENT) + "] took [" + stopWatch.stop().lastTaskTime() + "]");
                    stopWatch.start();
                }
            }

            log("Indexing took [" + stopWatch.totalTime() + "]");
            log("TPS [" + (((double) (NUM_PARENTS * (1 + NUM_CHILDREN_PER_PARENT))) / stopWatch.totalTime().secondsFrac()) + "]");
        } catch (Exception e) {
            log("Indices exist, wait for green");
            waitForGreen();
        }

        client.admin().indices().prepareRefresh().execute().actionGet();
        log("Number of docs in index: " + client.prepareSearch(PARENT_INDEX, CHILD_INDEX).setQuery(matchAllQuery()).setSize(0).execute().actionGet().getHits().getTotalHits());
        log("");
    }

    public void warmFieldData(String parentField, String childField) {
        ListenableActionFuture<SearchResponse> parentSearch = null;
        ListenableActionFuture<SearchResponse> childSearch = null;

        if (parentField != null) {
            parentSearch = client
                    .prepareSearch(PARENT_INDEX)
                    .setQuery(matchAllQuery()).addAggregation(terms("parentfield").field(parentField)).execute();
        }

        if (childField != null) {
            childSearch = client
                    .prepareSearch(CHILD_INDEX)
                    .setQuery(matchAllQuery()).addAggregation(terms("childfield").field(childField)).execute();
        }

        if (parentSearch != null) parentSearch.actionGet();
        if (childSearch != null) childSearch.actionGet();
    }

    public long runQuery(String name, int testNum, String index, long expectedHits, QueryBuilder query) {
        SearchResponse searchResponse = new CoordinateSearchRequestBuilder(client)
        .setIndices(index)
                .setQuery(query)
                .execute().actionGet();

        if (searchResponse.getFailedShards() > 0) {
            log("Search Failures " + Arrays.toString(searchResponse.getShardFailures()));
        }

        long hits = searchResponse.getHits().totalHits();
        if (hits != expectedHits) {
            log("[" + name + "][#" + testNum + "] Hits Mismatch:  expected [" + expectedHits + "], got [" + hits + "]");
        }

        return searchResponse.getTookInMillis();
    }

    /**
     * Search for parent documents that have children containing a specified tag.
     * Expect all parents returned since one child from each parent will match the lookup.
     * <p/>
     * Parent string field = "id"
     * Parent long field = "num"
     * Child string field = "pid"
     * Child long field = "num"
     */
    public void benchHasChildSingleTerm() {
      QueryBuilder lookupQuery;
      QueryBuilder mainQuery = matchAllQuery();

      FilterJoinBuilder stringFilter = QueryBuilders.filterJoin("id")
              .indices(CHILD_INDEX)
              .types(CHILD_TYPE)
              .path("pid")
              .termsEncoding(TermsByQueryRequest.TermsEncoding.LONG);

      FilterJoinBuilder longFilter = QueryBuilders.filterJoin("num")
              .indices(CHILD_INDEX)
              .types(CHILD_TYPE)
              .path("num")
              .termsEncoding(TermsByQueryRequest.TermsEncoding.LONG);

      FilterJoinBuilder intFilter = QueryBuilders.filterJoin("num")
              .indices(CHILD_INDEX)
              .types(CHILD_TYPE)
              .path("num")
              .termsEncoding(TermsByQueryRequest.TermsEncoding.INTEGER);

      FilterJoinBuilder bloomNumFilter = QueryBuilders.filterJoin("num")
              .indices(CHILD_INDEX)
              .types(CHILD_TYPE)
              .path("num")
              .termsEncoding(TermsByQueryRequest.TermsEncoding.BLOOM);

      FilterJoinBuilder bloomStringFilter = QueryBuilders.filterJoin("id")
              .indices(CHILD_INDEX)
              .types(CHILD_TYPE)
              .path("pid")
              .termsEncoding(TermsByQueryRequest.TermsEncoding.BLOOM);

      long tookString = 0;
      long tookLong = 0;
      long tookInt = 0;
      long tookBloomNum = 0;
      long tookBloomString = 0;
      long expected = NUM_PARENTS;
      warmFieldData("id", "pid");     // for string fields
      warmFieldData("num", "num");    // for long fields

      log("==== HAS CHILD SINGLE TERM ====");
      for (int i = 0; i < NUM_QUERIES; i++) {
        lookupQuery = boolQuery().filter(termQuery("tag", "tag" + random.nextInt(NUM_CHILDREN_PER_PARENT)));

        stringFilter.query(lookupQuery);
        longFilter.query(lookupQuery);
        intFilter.query(lookupQuery);
        bloomNumFilter.query(lookupQuery);
        bloomStringFilter.query(lookupQuery);

        tookString += runQuery("string", i, PARENT_INDEX, expected, stringFilter);
        tookLong += runQuery("long", i, PARENT_INDEX, expected, longFilter);
        tookInt += runQuery("int", i, PARENT_INDEX, expected, intFilter);
        tookBloomNum += runQuery("bloom_num", i, PARENT_INDEX, expected, bloomNumFilter);
        tookBloomString += runQuery("bloom_string", i, PARENT_INDEX, expected, bloomStringFilter);
      }

      log("string: " + (tookString / NUM_QUERIES) + "ms avg");
      log("long  : " + (tookLong / NUM_QUERIES) + "ms avg");
      log("int   : " + (tookInt / NUM_QUERIES) + "ms avg");
      log("bloom_num   : " + (tookBloomNum / NUM_QUERIES) + "ms avg");
      log("bloom_string   : " + (tookBloomString / NUM_QUERIES) + "ms avg");
      log("");
    }

    /**
     * Search for parent documents that have any child.
     * Expect all parent documents returned.
     * <p/>
     * Parent string field = "id"
     * Parent long field = "num"
     * Child string field = "pid"
     * Child long field = "num"
     */
    public void benchHasChildMatchAll() {
      QueryBuilder lookupQuery = matchAllQuery();
      QueryBuilder mainQuery = matchAllQuery();

      FilterJoinBuilder stringFilter = QueryBuilders.filterJoin("id")
              .indices(CHILD_INDEX)
              .types(CHILD_TYPE)
              .path("pid")
              .query(lookupQuery)
              .termsEncoding(TermsByQueryRequest.TermsEncoding.LONG);

      FilterJoinBuilder longFilter = QueryBuilders.filterJoin("num")
              .indices(CHILD_INDEX)
              .types(CHILD_TYPE)
              .path("num")
              .query(lookupQuery)
              .termsEncoding(TermsByQueryRequest.TermsEncoding.LONG);

      FilterJoinBuilder intFilter = QueryBuilders.filterJoin("num")
              .indices(CHILD_INDEX)
              .types(CHILD_TYPE)
              .path("num")
              .query(lookupQuery)
              .termsEncoding(TermsByQueryRequest.TermsEncoding.INTEGER);

      FilterJoinBuilder bloomNumFilter = QueryBuilders.filterJoin("num")
              .indices(CHILD_INDEX)
              .types(CHILD_TYPE)
              .path("num")
              .query(lookupQuery)
              .termsEncoding(TermsByQueryRequest.TermsEncoding.BLOOM);

      FilterJoinBuilder bloomStringFilter = QueryBuilders.filterJoin("id")
              .indices(CHILD_INDEX)
              .types(CHILD_TYPE)
              .path("pid")
              .query(lookupQuery)
              .termsEncoding(TermsByQueryRequest.TermsEncoding.BLOOM);

      long tookString = 0;
      long tookLong = 0;
      long tookInt = 0;
      long tookBloomNum = 0;
      long tookBloomString = 0;
      long expected = NUM_PARENTS;
      warmFieldData("id", "pid");     // for string fields
      warmFieldData("num", "num");    // for long fields

      log("==== HAS CHILD MATCH-ALL ====");
      for (int i = 0; i < NUM_QUERIES; i++) {
        tookString += runQuery("string", i, PARENT_INDEX, expected, stringFilter);
        tookLong += runQuery("long", i, PARENT_INDEX, expected, longFilter);
        tookInt += runQuery("int", i, PARENT_INDEX, expected, intFilter);
        tookBloomNum += runQuery("bloom_num", i, PARENT_INDEX, expected, bloomNumFilter);
        tookBloomString += runQuery("bloom_string", i, PARENT_INDEX, expected, bloomStringFilter);
      }

      log("string: " + (tookString / NUM_QUERIES) + "ms avg");
      log("long  : " + (tookLong / NUM_QUERIES) + "ms avg");
      log("int   : " + (tookInt / NUM_QUERIES) + "ms avg");
      log("bloom_num   : " + (tookBloomNum / NUM_QUERIES) + "ms avg");
      log("bloom_string   : " + (tookBloomString / NUM_QUERIES) + "ms avg");
      log("");
    }

    /**
     * Search for children that have a parent with the specified name.
     * Expect NUM_CHILDREN_PER_PARENT since only one parent matching lookup.
     * <p/>
     * Parent string field = "id"
     * Parent numeric field = "num"
     * Child string field = "pid"
     * Child numeric field = "num"
     */
    public void benchHasParentSingleTerm() {
      QueryBuilder lookupQuery;
      QueryBuilder mainQuery = matchAllQuery();

      FilterJoinBuilder stringFilter = QueryBuilders.filterJoin("pid")
              .indices(PARENT_INDEX)
              .types(PARENT_TYPE)
              .path("id")
              .termsEncoding(TermsByQueryRequest.TermsEncoding.LONG);

      FilterJoinBuilder longFilter = QueryBuilders.filterJoin("num")
              .indices(PARENT_INDEX)
              .types(PARENT_TYPE)
              .path("num")
              .termsEncoding(TermsByQueryRequest.TermsEncoding.LONG);

      FilterJoinBuilder intFilter = QueryBuilders.filterJoin("num")
              .indices(PARENT_INDEX)
              .types(PARENT_TYPE)
              .path("num")
              .termsEncoding(TermsByQueryRequest.TermsEncoding.INTEGER);

      FilterJoinBuilder bloomNumFilter = QueryBuilders.filterJoin("num")
              .indices(PARENT_INDEX)
              .types(PARENT_TYPE)
              .path("num")
              .termsEncoding(TermsByQueryRequest.TermsEncoding.BLOOM);

      FilterJoinBuilder bloomStringFilter = QueryBuilders.filterJoin("pid")
              .indices(PARENT_INDEX)
              .types(PARENT_TYPE)
              .path("id")
              .termsEncoding(TermsByQueryRequest.TermsEncoding.BLOOM);

      long tookString = 0;
      long tookLong = 0;
      long tookInt = 0;
      long tookBloomNum = 0;
      long tookBloomString = 0;
      long expected = NUM_CHILDREN_PER_PARENT;
      warmFieldData("id", "pid");     // for string fields
      warmFieldData("num", "num");    // for long fields

      log("==== HAS PARENT SINGLE TERM ====");
      for (int i = 0; i < NUM_QUERIES; i++) {
        lookupQuery = boolQuery().filter(termQuery("name", "test" + (random.nextInt(NUM_PARENTS) + 1)));

        stringFilter.query(lookupQuery);
        longFilter.query(lookupQuery);
        intFilter.query(lookupQuery);
        bloomNumFilter.query(lookupQuery);
        bloomStringFilter.query(lookupQuery);

        tookString += runQuery("string", i, CHILD_INDEX, expected, stringFilter);
        tookLong += runQuery("long", i, CHILD_INDEX, expected, longFilter);
        tookInt += runQuery("int", i, CHILD_INDEX, expected, intFilter);
        tookBloomNum += runQuery("bloom_num", i, CHILD_INDEX, expected, bloomNumFilter);
        tookBloomString += runQuery("bloom_string", i, CHILD_INDEX, expected, bloomStringFilter);
      }

      log("string: " + (tookString / NUM_QUERIES) + "ms avg");
      log("long  : " + (tookLong / NUM_QUERIES) + "ms avg");
      log("int   : " + (tookInt / NUM_QUERIES) + "ms avg");
      log("bloom_num   : " + (tookBloomNum / NUM_QUERIES) + "ms avg");
      log("bloom_string   : " + (tookBloomString / NUM_QUERIES) + "ms avg");
      log("");
    }

    /**
     * Search for children that have a parent.
     * Expect all children to be returned.
     * <p/>
     * Parent string field = "id"
     * Parent long field = "num"
     * Child string field = "pid"
     * Child long field = "num"
     */
    public void benchHasParentMatchAll() {
      QueryBuilder lookupQuery = matchAllQuery();
      QueryBuilder mainQuery = matchAllQuery();

      FilterJoinBuilder stringFilter = QueryBuilders.filterJoin("pid")
              .indices(PARENT_INDEX)
              .types(PARENT_TYPE)
              .path("id")
              .query(lookupQuery)
              .termsEncoding(TermsByQueryRequest.TermsEncoding.LONG);

      FilterJoinBuilder longFilter = QueryBuilders.filterJoin("num")
              .indices(PARENT_INDEX)
              .types(PARENT_TYPE)
              .path("num")
              .query(lookupQuery)
              .termsEncoding(TermsByQueryRequest.TermsEncoding.LONG);

      FilterJoinBuilder intFilter = QueryBuilders.filterJoin("num")
              .indices(PARENT_INDEX)
              .types(PARENT_TYPE)
              .path("num")
              .query(lookupQuery)
              .termsEncoding(TermsByQueryRequest.TermsEncoding.INTEGER);

      FilterJoinBuilder bloomNumFilter = QueryBuilders.filterJoin("num")
              .indices(PARENT_INDEX)
              .types(PARENT_TYPE)
              .path("num")
              .query(lookupQuery)
              .termsEncoding(TermsByQueryRequest.TermsEncoding.BLOOM);

      FilterJoinBuilder bloomStringFilter = QueryBuilders.filterJoin("pid")
              .indices(PARENT_INDEX)
              .types(PARENT_TYPE)
              .path("id")
              .query(lookupQuery)
              .termsEncoding(TermsByQueryRequest.TermsEncoding.BLOOM);

      long tookString = 0;
      long tookLong = 0;
      long tookInt = 0;
      long tookBloomNum = 0;
      long tookBloomString = 0;
      long expected = NUM_CHILDREN_PER_PARENT * NUM_PARENTS;
      warmFieldData("id", "pid");     // for string fields
      warmFieldData("num", "num");    // for numeric fields

      log("==== HAS PARENT MATCH-ALL ====");
      for (int i = 0; i < NUM_QUERIES; i++) {
        tookString += runQuery("string", i, CHILD_INDEX, expected, stringFilter);
        tookLong += runQuery("long", i, CHILD_INDEX, expected, longFilter);
        tookInt += runQuery("int", i, CHILD_INDEX, expected, intFilter);
        tookBloomNum += runQuery("bloom_num", i, CHILD_INDEX, expected, bloomNumFilter);
        tookBloomString += runQuery("bloom_string", i, CHILD_INDEX, expected, bloomStringFilter);
      }

      log("string: " + (tookString / NUM_QUERIES) + "ms avg");
      log("long  : " + (tookLong / NUM_QUERIES) + "ms avg");
      log("int   : " + (tookInt / NUM_QUERIES) + "ms avg");
      log("bloom_num   : " + (tookBloomNum / NUM_QUERIES) + "ms avg");
      log("bloom_string   : " + (tookBloomString / NUM_QUERIES) + "ms avg");
      log("");
    }

    /**
     * Search for children that have a parent with any of the specified names.
     * Expect NUM_CHILDREN_PER_PARENT * # of names.
     * <p/>
     * Parent string field = "id"
     * Parent long field = "num"
     * Child string field = "pid"
     * Child long field = "num"
     */
    public void benchHasParentRandomTerms() {
        QueryBuilder lookupQuery;
        QueryBuilder mainQuery = matchAllQuery();
        Set<String> names = new HashSet<>(NUM_PARENTS);

        FilterJoinBuilder stringFilter = QueryBuilders.filterJoin("pid")
                .indices(PARENT_INDEX)
                .types(PARENT_TYPE)
                .path("id");

        FilterJoinBuilder longFilter = QueryBuilders.filterJoin("num")
                .indices(PARENT_INDEX)
                .types(PARENT_TYPE)
                .path("num");

        long tookString = 0;
        long tookLong = 0;
        int expected = 0;
        warmFieldData("id", "pid");     // for string fields
        warmFieldData("num", "num");    // for long fields
        warmFieldData("name", null);    // for field data terms filter

        log("==== HAS PARENT RANDOM TERMS ====");
        for (int i = 0; i < NUM_QUERIES; i++) {

            // add a random number of terms to the set on each iteration
            int randNum = random.nextInt(NUM_PARENTS / NUM_QUERIES) + 1;
            for (int j = 0; j < randNum; j++) {
                names.add("test" + (random.nextInt(NUM_PARENTS) + 1));
            }

            lookupQuery = boolQuery().filter(termsQuery("name", names));
            expected = NUM_CHILDREN_PER_PARENT * names.size();
            stringFilter.query(lookupQuery);
            longFilter.query(lookupQuery);

            tookString += runQuery("string", i, CHILD_INDEX, expected, stringFilter);
            tookLong += runQuery("long", i, CHILD_INDEX, expected, longFilter);
        }

        log("string: " + (tookString / NUM_QUERIES) + "ms avg");
        log("long  : " + (tookLong / NUM_QUERIES) + "ms avg");
        log("");
    }
}
