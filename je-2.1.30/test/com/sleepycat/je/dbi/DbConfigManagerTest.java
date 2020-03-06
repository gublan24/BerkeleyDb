/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Sleepycat Software.  All rights reserved.
 *
 * $Id: DbConfigManagerTest.java,v 1.25 2006/01/03 21:56:13 bostic Exp $
 */

package com.sleepycat.je.dbi;

import junit.framework.TestCase;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.util.TestUtils;

public class DbConfigManagerTest extends TestCase {

    /**
     * Test that parameter defaults work, that we can add and get
     * parameters
     */
    public void testBasicParams()
	throws DatabaseException {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setCacheSize(2000);
        DbConfigManager configManager = new DbConfigManager(envConfig);

        /**
         * Longs: The config manager should return the value for an
         * explicitly set param and the default for one not set.
         *
         */
        assertEquals(2000,
                     configManager.getLong(EnvironmentParams.MAX_MEMORY));
        assertEquals(EnvironmentParams.ENV_RECOVERY.getDefault(),
                     configManager.get(EnvironmentParams.ENV_RECOVERY));
    }
}
