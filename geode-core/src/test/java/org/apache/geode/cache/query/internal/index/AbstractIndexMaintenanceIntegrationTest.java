/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.geode.cache.query.internal.index;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.apache.geode.cache.query.FunctionDomainException;
import org.apache.geode.cache.query.NameResolutionException;
import org.apache.geode.cache.query.Query;
import org.apache.geode.cache.query.QueryInvocationTargetException;
import org.apache.geode.cache.query.SelectResults;
import org.apache.geode.cache.query.TypeMismatchException;
import org.apache.geode.cache.query.internal.QueryObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.query.CacheUtils;
import org.apache.geode.cache.query.IndexExistsException;
import org.apache.geode.cache.query.IndexNameConflictException;
import org.apache.geode.cache.query.QueryService;
import org.apache.geode.cache.query.RegionNotFoundException;
import org.apache.geode.cache.query.data.PortfolioPdx;
import org.apache.geode.internal.cache.LocalRegion;
import org.apache.geode.internal.cache.RegionEntry;
import org.apache.geode.test.junit.categories.IntegrationTest;

@Category(IntegrationTest.class)
public abstract class AbstractIndexMaintenanceIntegrationTest {

  private Cache cache;
  private QueryService queryService;

  @Before
  public void setUp() throws Exception {
    CacheUtils.startCache();
    cache = CacheUtils.getCache();
    queryService = cache.getQueryService();
  }


  @After
  public void tearDown() throws Exception {
    CacheUtils.closeCache();
  }

  @Test
  public void whenRemovingRegionEntryFromIndexIfEntryDestroyedIsThrownCorrectlyRemoveFromIndexAndNotThrowException() throws Exception {
    LocalRegion region = (LocalRegion)cache.createRegionFactory(RegionShortcut.REPLICATE).create("portfolios");
    AbstractIndex statusIndex = createIndex(queryService, "statusIndex", "value.status", "/portfolios.entrySet()");

    PortfolioPdx p = new PortfolioPdx(1);
    region.put("KEY-1", p);
    RegionEntry entry = region.getRegionEntry("KEY-1");
    region.destroy("KEY-1");

    statusIndex.removeIndexMapping(entry, IndexProtocol.OTHER_OP);
  }

  @Test
  public void queryReturnsEntryThatIsInIndex() throws Exception {
    LocalRegion region = (LocalRegion)cache.createRegionFactory(RegionShortcut.REPLICATE).create("portfolios");
    AbstractIndex statusIndex = createIndex(queryService, "statusIndex", "status", "/portfolios");

    PortfolioPdx portfolio0 = new PortfolioPdx(0);
    PortfolioPdx portfolio1 = new PortfolioPdx(1);
    region.put(0, portfolio0);
    region.put(1, portfolio1);
    executeQuery("select * from /portfolios where status='active'", portfolio0);
    assertEquals(1,statusIndex.getStatistics().getTotalUses());
  }

  @Test
  public void queryDoesNotReturnDestroyedEntry() throws Exception {
    LocalRegion region = (LocalRegion)cache.createRegionFactory(RegionShortcut.REPLICATE).create("portfolios");
    AbstractIndex statusIndex = createIndex(queryService, "statusIndex", "status", "/portfolios");

    PortfolioPdx portfolio0 = new PortfolioPdx(0);
    region.put(0, portfolio0);
    region.destroy(0);
    executeQuery("select * from /portfolios where status='active'");
    assertEquals(1,statusIndex.getStatistics().getTotalUses());
  }

  @Test
  public void queryDoesNotReturnUpdatedEntryThatNoLongerMatchesExpression() throws Exception {
    LocalRegion region = (LocalRegion)cache.createRegionFactory(RegionShortcut.REPLICATE).create("portfolios");
    AbstractIndex statusIndex = createIndex(queryService, "statusIndex", "status", "/portfolios");

    PortfolioPdx portfolio0 = new PortfolioPdx(0);
    region.put(0, portfolio0);
    region.put(0, new PortfolioPdx(1));
    executeQuery("select * from /portfolios where status='active'");
    assertEquals(1,statusIndex.getStatistics().getTotalUses());
  }

  @Test
  public void queryReturnsUpdatedEntryThatNowMatchesExpression() throws Exception {
    LocalRegion region = (LocalRegion)cache.createRegionFactory(RegionShortcut.REPLICATE).create("portfolios");
    AbstractIndex statusIndex = createIndex(queryService, "statusIndex", "status", "/portfolios");

    PortfolioPdx portfolio0 = new PortfolioPdx(0);
    region.put(0, new PortfolioPdx(1));
    region.put(0, portfolio0);
    assertEquals(0,statusIndex.getStatistics().getTotalUses());
    executeQuery("select * from /portfolios where status='active'", portfolio0);
    assertEquals(1,statusIndex.getStatistics().getTotalUses());
  }

  private void executeQuery(String queryString, final Object...expected)
    throws FunctionDomainException, TypeMismatchException, NameResolutionException, QueryInvocationTargetException
  {
    Query query = queryService.newQuery(queryString);
    SelectResults results = (SelectResults) query.execute();

    assertEquals(new HashSet(Arrays.asList(expected)), results.asSet());
  }

  protected abstract AbstractIndex createIndex(final QueryService qs, String name, String indexExpression, String regionPath)
    throws IndexNameConflictException, IndexExistsException, RegionNotFoundException ;
}
