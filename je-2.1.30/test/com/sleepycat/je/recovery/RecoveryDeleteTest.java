/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Sleepycat Software.  All rights reserved.
 *
 * $Id: RecoveryDeleteTest.java,v 1.4 2006/01/03 21:56:24 bostic Exp $
 */

package com.sleepycat.je.recovery;

import java.util.Hashtable;

import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;

public class RecoveryDeleteTest extends RecoveryTestBase {

    protected void setExtraProperties() 
        throws DatabaseException {
        envConfig.setConfigParam(
                      EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(),
                      "false");
    }

    /* Make sure that we can recover after the entire tree is compressed away. */
    public void testDeleteAllAndCompress()
        throws Throwable {

        createEnvAndDbs(1 << 20, false, NUM_DBS);
        int numRecs = 10;

        try {
            // Set up an repository of expected data
            Hashtable expectedData = new Hashtable();
            
            // insert all the data
            Transaction txn = env.beginTransaction(null, null);
            insertData(txn, 0, numRecs -1 , expectedData, 1, true, NUM_DBS);
            txn.commit();

            /* 
             * Do two checkpoints here so that the INs that make up this new
             * tree are not in the redo part of the log.
             */
            CheckpointConfig ckptConfig = new CheckpointConfig();
            ckptConfig.setForce(true);
            env.checkpoint(ckptConfig);
            env.checkpoint(ckptConfig);
            txn = env.beginTransaction(null, null);
            insertData(txn, numRecs, numRecs + 1, expectedData, 1, true, NUM_DBS);
            txn.commit();

            /* delete all */
            txn = env.beginTransaction(null, null);
            deleteData(txn, expectedData, true, true, NUM_DBS);
            txn.commit();

            /* This will remove the root. */
            env.compress();

            closeEnv();
            recoverAndVerify(expectedData, NUM_DBS);
        } catch (Throwable t) {
            // print stacktrace before trying to clean up files
            t.printStackTrace();
            throw t;
        }
    }
}
