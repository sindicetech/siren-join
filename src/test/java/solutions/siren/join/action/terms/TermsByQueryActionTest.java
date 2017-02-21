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
package solutions.siren.join.action.terms;

import com.carrotsearch.hppc.cursors.LongCursor;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.test.ESIntegTestCase;
import solutions.siren.join.SirenJoinTestCase;
import solutions.siren.join.action.terms.collector.LongBloomFilter;
import solutions.siren.join.action.terms.collector.LongTermsSet;
import solutions.siren.join.action.terms.collector.NumericTermsSet;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.test.hamcrest.ElasticsearchAssertions;
import org.junit.Test;
import solutions.siren.join.action.terms.collector.TermsSet;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.hamcrest.Matchers.*;

@ESIntegTestCase.ClusterScope(scope= ESIntegTestCase.Scope.SUITE, numDataNodes=1)
public class TermsByQueryActionTest extends SirenJoinTestCase {

  /**
   * Tests that the terms by query action returns the correct terms against string fields
   */
  @Test
  public void testTermsByQueryStringField() throws Exception {
    ElasticsearchAssertions.assertAcked(prepareCreate("test").addMapping("type", "str", "type=keyword"));
    int numDocs = RandomizedTest.randomIntBetween(100, 2000);
    logger.info("--> indexing [" + numDocs + "] docs");
    for (int i = 0; i < numDocs; i++) {
      client().prepareIndex("test", "type", "" + i)
              .setSource(jsonBuilder().startObject()
                                        .field("str", Integer.toString(i))
                                      .endObject())
              .execute().actionGet();
    }

    client().admin().indices().prepareRefresh("test").execute().actionGet();

    logger.info("--> lookup terms in field [str]");
    TermsByQueryResponse resp = new TermsByQueryRequestBuilder(client(), TermsByQueryAction.INSTANCE).setIndices("test")
                                                                        .setField("str")
                                                                        .setQuery(QueryBuilders.matchAllQuery())
                                                                        .setTermsEncoding(TermsByQueryRequest.TermsEncoding.LONG)
                                                                        .execute()
                                                                        .actionGet();

    ElasticsearchAssertions.assertNoFailures(resp);
    assertThat(resp.getEncodedTermsSet(), notNullValue());
    assertThat(resp.getSize(), is(numDocs));
    TermsSet lTerms = NumericTermsSet.readFrom(resp.getEncodedTermsSet());
    assertThat(lTerms instanceof LongTermsSet, is(true));
    for (int i = 0; i < numDocs; i++) {
      BytesRef bytesRef = new BytesRef(Integer.toString(i));
      long termHash = LongBloomFilter.hash3_x64_128(bytesRef.bytes, bytesRef.offset, bytesRef.length, 0);
      assertThat(((LongTermsSet) lTerms).contains(termHash), is(true));
    }
  }

  /**
   * Tests that the terms by query action returns the correct terms against integer fields
   */
  @Test
  public void testTermsByQueryIntegerField() throws Exception {
    createIndex("test");

    int numDocs = RandomizedTest.randomIntBetween(100, 2000);
    logger.info("--> indexing [" + numDocs + "] docs");
    for (int i = 0; i < numDocs; i++) {
      client().prepareIndex("test", "type", "" + i)
              .setSource(jsonBuilder().startObject()
                                        .field("int", i)
                                      .endObject())
              .execute().actionGet();
    }

    client().admin().indices().prepareRefresh("test").execute().actionGet();

    logger.info("--> lookup terms in field [int]");
    TermsByQueryResponse resp = new TermsByQueryRequestBuilder(client(), TermsByQueryAction.INSTANCE).setIndices("test")
                                                                        .setField("int")
                                                                        .setQuery(QueryBuilders.matchAllQuery())
                                                                        .setTermsEncoding(TermsByQueryRequest.TermsEncoding.LONG)
                                                                        .execute()
                                                                        .actionGet();

    ElasticsearchAssertions.assertNoFailures(resp);
    assertThat(resp.getEncodedTermsSet(), notNullValue());
    assertThat(resp.getSize(), is(numDocs));
    TermsSet lTerms = TermsSet.readFrom(resp.getEncodedTermsSet());
    assertThat(lTerms instanceof LongTermsSet, is(true));
    assertThat(lTerms.size(), is(numDocs));
    for (int i = 0; i < numDocs; i++) {
      assertThat(((LongTermsSet) lTerms).contains(Long.valueOf(i)), is(true));
    }
  }

  /**
   * Tests that the limit for the number of terms retrieved is properly applied.
   */
  @Test
  public void testTermsByQueryWithLimit() throws Exception {
    createIndex("test");

    int numDocs = RandomizedTest.randomIntBetween(100, 2000);
    logger.info("--> indexing [" + numDocs + "] docs");
    for (int i = 0; i < numDocs; i++) {
      client().prepareIndex("test", "type", "" + i)
              .setSource(jsonBuilder().startObject()
                                        .field("int", i)
                                      .endObject())
              .execute().actionGet();
    }

    client().admin().indices().prepareRefresh("test").execute().actionGet();

    logger.info("--> lookup terms in field [int]");
    TermsByQueryResponse resp = new TermsByQueryRequestBuilder(client(), TermsByQueryAction.INSTANCE).setIndices("test")
                                                                        .setField("int")
                                                                        .setQuery(QueryBuilders.matchAllQuery())
                                                                        .setOrderBy(TermsByQueryRequest.Ordering.DEFAULT)
                                                                        .setMaxTermsPerShard(50)
                                                                        .setTermsEncoding(TermsByQueryRequest.TermsEncoding.LONG)
                                                                        .execute()
                                                                        .actionGet();

    int expectedMaxResultSize = this.getNumShards("test").totalNumShards * 50;
    ElasticsearchAssertions.assertNoFailures(resp);
    assertThat(resp.getEncodedTermsSet(), notNullValue());
    assertThat(resp.getSize(), lessThanOrEqualTo(expectedMaxResultSize));
    TermsSet lTerms = TermsSet.readFrom(resp.getEncodedTermsSet());
    assertThat(lTerms instanceof LongTermsSet, is(true));
  }

  /**
   * Tests the ordering by document score.
   */
  @Test
  public void testTermsByQueryWithLimitOrderByDocScore() throws Exception {
    // Enforce one single shard for index as it is difficult with multiple shards
    // to avoid having one shard with less than 5 even ids (i.e., to avoid the shard
    // returning odd ids.
    Map<String, Object> indexSettings = new HashMap<>();
    indexSettings.put("number_of_shards", 1);
    assertAcked(prepareCreate("test").setSettings(indexSettings));

    int numDocs = RandomizedTest.randomIntBetween(100, 2000);
    logger.info("--> indexing [" + numDocs + "] docs");
    for (int i = 0; i < numDocs / 2; i += 2) {
      client().prepareIndex("test", "type", "" + i)
              .setSource(jsonBuilder().startObject()
              .field("int", i)
              .field("text", "aaa")
              .endObject())
              .execute().actionGet();
    }

    for (int i = 1; i < numDocs / 2; i += 2) {
      client().prepareIndex("test", "type", "" + i)
              .setSource(jsonBuilder().startObject()
              .field("int", i)
              .field("text", "aaa aaa")
              .endObject())
              .execute().actionGet();
    }

    client().admin().indices().prepareRefresh("test").execute().actionGet();

    logger.info("--> lookup terms in field [int]");
    TermsByQueryResponse resp = new TermsByQueryRequestBuilder(client(), TermsByQueryAction.INSTANCE).setIndices("test")
                                                                        .setField("int")
                                                                        .setQuery(QueryBuilders.termQuery("text", "aaa"))
                                                                        .setOrderBy(TermsByQueryRequest.Ordering.DOC_SCORE)
                                                                        .setMaxTermsPerShard(5)
                                                                        .setTermsEncoding(TermsByQueryRequest.TermsEncoding.LONG)
                                                                        .execute()
                                                                        .actionGet();

    int expectedMaxResultSize = this.getNumShards("test").totalNumShards * 5;
    ElasticsearchAssertions.assertNoFailures(resp);
    assertThat(resp.getEncodedTermsSet(), notNullValue());
    assertThat(resp.getSize(), lessThanOrEqualTo(expectedMaxResultSize));
    TermsSet lTerms = NumericTermsSet.readFrom(resp.getEncodedTermsSet());
    assertThat(lTerms instanceof LongTermsSet, is(true));

    // If the ordering by document score worked, we should only have documents with text = aaa (even ids), and no
    // documents with text = aaa aaa (odd ids), as the first one will be ranked higher.

    Iterator<LongCursor> it = ((LongTermsSet) lTerms).getLongHashSet().iterator();
    while (it.hasNext()) {
      long value = it.next().value;
      assertThat(value % 2 == 0, is(true));
    }
  }

  /**
   * Tests the validation of the request when terms encodign si set to bytes. If no maxTermsPerShard is specified,
   * it should fail with a {@link ActionRequestValidationException}.
   */
  @Test(expected=ActionRequestValidationException.class)
  public void testRequestValidationWithBytesTermsEncoding() throws Exception {
    createIndex("test");

    TermsByQueryResponse resp = new TermsByQueryRequestBuilder(client(), TermsByQueryAction.INSTANCE).setIndices("test")
            .setField("int")
            .setQuery(QueryBuilders.matchAllQuery())
            .setTermsEncoding(TermsByQueryRequest.TermsEncoding.BYTES)
            .execute()
            .actionGet();
  }

}