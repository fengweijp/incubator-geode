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

import org.junit.After;
import org.junit.Before;

/**
 * Runs all the tests of {@link CompactRangeIndexMaintenanceIntegrationTest}, but
 * with reevaluation of the query expression after getting an entry from the index is disabled.
 * This ensures we are actually removing the entries from the index.
 */
public class CompactRangeIndexMaintenanceNoReevaluationIntegrationTest extends CompactRangeIndexMaintenanceIntegrationTest {
  private long originalWindow;

  @Before
  public void disableReevaluation() {
    originalWindow = IndexManager.IN_PROGRESS_UPDATE_WINDOW;
    IndexManager.IN_PROGRESS_UPDATE_WINDOW = Long.MIN_VALUE;

  }

  @After
  public void enableReevaluation() {
    IndexManager.IN_PROGRESS_UPDATE_WINDOW = originalWindow;
  }


}
