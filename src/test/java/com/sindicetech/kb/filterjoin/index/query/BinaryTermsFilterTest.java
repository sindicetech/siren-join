/**
 * Copyright (c) 2015 Renaud Delbru. All Rights Reserved.
 */
package com.sindicetech.kb.filterjoin.index.query;

import com.sindicetech.kb.filterjoin.FilterJoinTestCase;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.hash.Hashing;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Test;

import static com.sindicetech.kb.filterjoin.index.query.FilterBuilders.binaryTermsFilter;
import static org.elasticsearch.index.query.QueryBuilders.filteredQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;

@ElasticsearchIntegrationTest.ClusterScope(scope=ElasticsearchIntegrationTest.Scope.SUITE, numDataNodes=1)
public class BinaryTermsFilterTest extends FilterJoinTestCase {

  @Test
  public void testStringSerialization() throws Exception {
    // check that the byte array is not serialised
    assertTrue(binaryTermsFilter("id", new long[] { 1, 2, 4, 8, 10, 7, 6, 11, 5 }, "cache_key").toString().contains("[size=76]"));
  }

  @Test
  public void testSimpleFilter() throws Exception {
    Settings settings = ImmutableSettings.settingsBuilder().put("number_of_shards", 1).build();
    assertAcked(prepareCreate("index1").setSettings(settings).addMapping("type", "id", "type=integer"));
    ensureGreen();

    indexRandom(true,
    client().prepareIndex("index1", "type", "1").setSource("id", "1"),
    client().prepareIndex("index1", "type", "3").setSource("id", "3"),
    client().prepareIndex("index1", "type", "7").setSource("id", "7"));

    SearchResponse searchResponse = client().prepareSearch("index1").setQuery(
      filteredQuery(matchAllQuery(),
                    binaryTermsFilter("id", new long[] { 1, 2, 4, 8, 10, 7, 6, 11, 5 }, "cache_key")
      )
    ).get();
    assertHitCount(searchResponse, 2L);
  }

  @Test
  public void testStringFilter() throws Exception {
    Settings settings = ImmutableSettings.settingsBuilder().put("number_of_shards", 1).build();
    assertAcked(prepareCreate("index1").setSettings(settings).addMapping("type", "id", "type=string"));
    ensureGreen();

    indexRandom(true,
    client().prepareIndex("index1", "type", "1").setSource("id", "1"),
    client().prepareIndex("index1", "type", "3").setSource("id", "3"),
    client().prepareIndex("index1", "type", "7").setSource("id", "7"));

    long[] ids = new long[] { 1, 2, 4, 8, 10, 7, 6, 11, 5 };
    long[] hashIds = new long[ids.length];
    for (int i = 0; i < ids.length; i++) {
      hashIds[i] = Hashing.sipHash24().hashBytes(Long.toString(ids[i]).getBytes()).asLong();
    }

    SearchResponse searchResponse = client().prepareSearch("index1").setQuery(
      filteredQuery(matchAllQuery(),
                    binaryTermsFilter("id", hashIds, "cache_key")
      )
    ).get();
    assertHitCount(searchResponse, 2L);
  }

}
