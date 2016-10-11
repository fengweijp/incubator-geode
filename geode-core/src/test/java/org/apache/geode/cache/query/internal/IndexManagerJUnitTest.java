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
/**
 */
package org.apache.geode.cache.query.internal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.apache.geode.cache.Region;
import org.apache.geode.cache.query.CacheUtils;
import org.apache.geode.cache.query.IndexType;
import org.apache.geode.cache.query.QueryService;
import org.apache.geode.cache.query.data.Portfolio;
import org.apache.geode.cache.query.internal.index.IndexData;
import org.apache.geode.cache.query.internal.index.IndexManager;
import org.apache.geode.cache.query.internal.index.IndexUtils;
import org.apache.geode.test.junit.categories.IntegrationTest;

@Category(IntegrationTest.class)
public class IndexManagerJUnitTest
{
  public long originalUpdateWindow;

  @Before
  public void setUp() throws java.lang.Exception
  {
    CacheUtils.startCache();
    Region region = CacheUtils.createRegion("portfolios", Portfolio.class);
    for (int i = 0; i < 4; i++) {
      region.put("" + i, new Portfolio(i));
      // CacheUtils.log(new Portfolio(i));
    }
    originalUpdateWindow = IndexManager.IN_PROGRESS_UPDATE_WINDOW;

  }

  @After
  public void tearDown() throws java.lang.Exception
  {
    CacheUtils.closeCache();
    IndexManager.IN_PROGRESS_UPDATE_WINDOW = originalUpdateWindow;
  }
  
  /**
   * This test is for a fix for #47475
   * We added a way to determine whether to reevaluate an entry for query execution
   * The method is to keep track of the delta and current time in a single long value
   * The value is then used by the query to determine if a region entry needs to be reevaluated
   * based on subtracting the value with the query execution time.  This provides a delta + some false positive time
   * If the delta + last modified time of the region entry is > query start time, 
   * we can assume that it needs to be reevaluated
   */
  @Test
  public void testSafeQueryTime() {

    IndexManager.IN_PROGRESS_UPDATE_WINDOW = 10;
    //fake query start at actual time of 9, 10, 11 and using the fake LMT of 0
    assertTrue(IndexManager.needsRecalculation(9, 0));
    assertTrue(IndexManager.needsRecalculation(10, 0));
    assertFalse(IndexManager.needsRecalculation(11, 0));
  }


  @Test
  public void testBestIndexPick() throws Exception
  {
    QueryService qs;

    qs = CacheUtils.getQueryService();
    qs.createIndex("statusIndex", IndexType.FUNCTIONAL, "status",
        "/portfolios, positions");
    QCompiler compiler = new QCompiler();
    List list = compiler.compileFromClause("/portfolios pf");
    ExecutionContext context = new QueryExecutionContext(null, CacheUtils.getCache());
    context.newScope(context.assosciateScopeID());

    Iterator iter = list.iterator();
    while (iter.hasNext()) {
      CompiledIteratorDef iterDef = (CompiledIteratorDef)iter.next();
      context.addDependencies(new CompiledID("dummy"), iterDef.computeDependencies(context));
      RuntimeIterator rIter = iterDef.getRuntimeIterator(context);
      context.bindIterator(rIter);
      context.addToIndependentRuntimeItrMap(iterDef);
    }
    CompiledPath cp = new CompiledPath(new CompiledID("pf"), "status");

    // TASK ICM1
    String[] defintions = { "/portfolios", "index_iter1.positions" };
    IndexData id = IndexUtils.findIndex("/portfolios", defintions, cp, "*",
        CacheUtils.getCache(), true, context);
    Assert.assertEquals(id.getMatchLevel(), 0);
    Assert.assertEquals(id.getMapping()[0], 1);
    Assert.assertEquals(id.getMapping()[1], 2);
    String[] defintions1 = { "/portfolios" };
    IndexData id1 = IndexUtils.findIndex("/portfolios", defintions1, cp, "*",
        CacheUtils.getCache(), true, context);
    Assert.assertEquals(id1.getMatchLevel(), -1);
    Assert.assertEquals(id1.getMapping()[0], 1);
    String[] defintions2 = { "/portfolios", "index_iter1.positions",
        "index_iter1.coll1" };
    IndexData id2 = IndexUtils.findIndex("/portfolios", defintions2, cp, "*",
        CacheUtils.getCache(), true, context);
    Assert.assertEquals(id2.getMatchLevel(), 1);
    Assert.assertEquals(id2.getMapping()[0], 1);
    Assert.assertEquals(id2.getMapping()[1], 2);
    Assert.assertEquals(id2.getMapping()[2], 0);

  }

}
