/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Sleepycat Software.  All rights reserved.
 *
 * $Id: SortedLSNTreeWalkerTest.java,v 1.6 2006/04/10 14:52:12 linda Exp $
 */

package com.sleepycat.je.dbi;

import java.io.File;

import junit.framework.TestCase;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.je.BtreeStats;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.SortedLSNTreeWalker.TreeNodeProcessor;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.util.TestUtils;
import com.sleepycat.je.utilint.DbLsn;

public class SortedLSNTreeWalkerTest extends TestCase {
    private static boolean DEBUG = false;

    /* Use small NODE_MAX to cause lots of splits. */
    private static final int NODE_MAX = 6;
    private static final int N_RECS = 30;

    private File envHome;
    private Environment env;
    private Database db;

    public SortedLSNTreeWalkerTest()
        throws Exception {

        envHome = new File(System.getProperty(TestUtils.DEST_DIR));
    }

    public void setUp()
        throws Exception {

        TestUtils.removeFiles("Setup", envHome, FileManager.JE_SUFFIX);
    }

    public void tearDown()
	throws Exception {

        if (env != null) {
            try {
                env.close();
            } catch (Exception e) {
                System.err.println("TearDown: " + e);
            }
        }
        env = null;
        TestUtils.removeFiles("TearDown", envHome, FileManager.JE_SUFFIX);
    }

    public void testSortedLSNTreeWalkerNoDupsReadingINList()
        throws Throwable {

	open(false);
	writeData(false);
	BtreeStats stats = (BtreeStats) db.getStats(null);
	if (DEBUG) {
	    System.out.println("***************");
	    DbInternal.dbGetDatabaseImpl(db).getTree().dump();
	}
	close();
	if (DEBUG) {
	    System.out.println("***************");
	}
	open(false);
	readData();
	if (DEBUG) {
	    DbInternal.dbGetDatabaseImpl(db).getTree().dump();
	    System.out.println("***************");
	}
	DatabaseImpl dbImpl = DbInternal.dbGetDatabaseImpl(db);
	db.close();
	db = null;
	assertEquals(N_RECS, walkTree(dbImpl, false, stats));
	close();
    }

    public void testSortedLSNTreeWalkerNoDups()
        throws Throwable {

	open(false);
	writeData(false);
	if (DEBUG) {
	    System.out.println("***************");
	    DbInternal.dbGetDatabaseImpl(db).getTree().dump();
	}
	BtreeStats stats = (BtreeStats) db.getStats(null);
	close();
	if (DEBUG) {
	    System.out.println("***************");
	}
	open(false);
	readData();
	if (DEBUG) {
	    DbInternal.dbGetDatabaseImpl(db).getTree().dump();
	    System.out.println("***************");
	}
	DatabaseImpl dbImpl = DbInternal.dbGetDatabaseImpl(db);
        db.close();
        db = null;
	assertEquals(N_RECS, walkTree(dbImpl, false, stats));
	close();
    }

    public void testSortedLSNTreeWalkerNoDupsDupsAllowed()
        throws Throwable {

	open(true);
	writeData(false);
	if (DEBUG) {
	    System.out.println("***************");
	    DbInternal.dbGetDatabaseImpl(db).getTree().dump();
	}
	BtreeStats stats = (BtreeStats) db.getStats(null);
	close();
	if (DEBUG) {
	    System.out.println("***************");
	}
	open(true);
	if (DEBUG) {
	    DbInternal.dbGetDatabaseImpl(db).getTree().dump();
	    System.out.println("***************");
	}
	DatabaseImpl dbImpl = DbInternal.dbGetDatabaseImpl(db);
        db.close();
        db = null;
	assertEquals(N_RECS, walkTree(dbImpl, false, stats));
	close();
    }

    public void testSortedLSNTreeWalkerDups()
        throws Throwable {

	open(true);
	writeData(true);
	BtreeStats stats = (BtreeStats) db.getStats(null);
	close();
	open(true);
	DatabaseImpl dbImpl = DbInternal.dbGetDatabaseImpl(db);
        db.close();
        db = null;
	assertEquals(N_RECS * 2, walkTree(dbImpl, true, stats));
	close();
    }

    public void testSortedLSNTreeWalkerDupsReadingINList()
        throws Throwable {

	open(true);
	writeData(true);
	BtreeStats stats = (BtreeStats) db.getStats(null);
	close();
	open(true);
	readData();
	DatabaseImpl dbImpl = DbInternal.dbGetDatabaseImpl(db);
	db.close();
	db = null;
	assertEquals(N_RECS * 2, walkTree(dbImpl, false, stats));
	close();
    }

    public void testSortedLSNTreeWalkerPendingDeleted()
        throws Throwable {

	open(true);
	int numRecs = writeDataWithDeletes();
	BtreeStats stats = (BtreeStats) db.getStats(null);
	close();
	open(true);
	readData();
	DatabaseImpl dbImpl = DbInternal.dbGetDatabaseImpl(db);
	db.close();
	db = null;
	assertEquals(numRecs, walkTree(dbImpl, false, stats));
	close();
    }

    private void open(boolean allowDuplicates)
        throws Exception {

        EnvironmentConfig envConfig = TestUtils.initEnvConfig();
        envConfig.setConfigParam
            (EnvironmentParams.NODE_MAX.getName(), String.valueOf(NODE_MAX));
	/*
        envConfig.setConfigParam
            (EnvironmentParams.MAX_MEMORY.getName(), "10000000");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CLEANER.getName(), "false");
        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_CHECKPOINTER.getName(), "false");
	*/

        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_EVICTOR.getName(), "false");

        envConfig.setConfigParam
            (EnvironmentParams.ENV_RUN_INCOMPRESSOR.getName(), "false");

        envConfig.setTransactional(true);
        envConfig.setAllowCreate(true);
        env = new Environment(envHome, envConfig);

        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setExclusiveCreate(false);
        dbConfig.setTransactional(true);
        dbConfig.setSortedDuplicates(allowDuplicates);
        db = env.openDatabase(null, "testDb", dbConfig);
    }

    private void writeData(boolean dups)
	throws DatabaseException {

	DatabaseEntry key = new DatabaseEntry();
	DatabaseEntry data = new DatabaseEntry();
	for (int i = 0; i < N_RECS; i++) {
	    IntegerBinding.intToEntry(i, key);
	    data.setData(new byte[1000]);
	    assertEquals(db.put(null, key, data),
			 OperationStatus.SUCCESS);
	    if (dups) {
		IntegerBinding.intToEntry(i + N_RECS + N_RECS, data);
		assertEquals(db.put(null, key, data),
			     OperationStatus.SUCCESS);
	    }
	}
    }

    private int writeDataWithDeletes()
	throws DatabaseException {

	DatabaseEntry key = new DatabaseEntry();
	DatabaseEntry data = new DatabaseEntry();
        int numInserted = 0;
        
        data.setData(new byte[10]);

	for (int i = 0; i < N_RECS; i++) {
	    IntegerBinding.intToEntry(i, key);
            Transaction txn = env.beginTransaction(null, null);
	    assertEquals(db.put(txn, key, data),
			 OperationStatus.SUCCESS);
            boolean deleted = false;
            if ((i%2) ==0) {
                assertEquals(db.delete(txn, key),
                             OperationStatus.SUCCESS);
                deleted = true;
            } 
            if ((i%3)== 0){
                txn.abort();
            } else {
                txn.commit();
                if (!deleted) {
                    numInserted++;
                }
            }
	}
        return numInserted;
    }

    private void readData()
	throws DatabaseException {

	DatabaseEntry key = new DatabaseEntry();
	DatabaseEntry data = new DatabaseEntry();
	IntegerBinding.intToEntry(N_RECS - 1, key);
	assertEquals(db.get(null, key, data, LockMode.DEFAULT),
		     OperationStatus.SUCCESS);
    }

    private void scanData()
	throws DatabaseException {

	DatabaseEntry key = new DatabaseEntry();
	DatabaseEntry data = new DatabaseEntry();
	Cursor cursor = db.openCursor(null, null);
	while (cursor.getNext(key, data, LockMode.DEFAULT) ==
	       OperationStatus.SUCCESS) {
	}
	cursor.close();
    }

    /* Return the number of keys seen in all BINs. */
    private int walkTree(DatabaseImpl dbImpl, boolean dups, BtreeStats stats)
	throws DatabaseException {

	TestingTreeNodeProcessor tnp = new TestingTreeNodeProcessor() {
		public void processLSN(long childLSN, LogEntryType childType) {

		    if (DEBUG) {
			System.out.println
			    (childType + " " + DbLsn.toString(childLSN));
		    }
		    if (childType.equals(LogEntryType.LOG_DBIN)) {
			dbinCount++;
		    } else if (childType.equals(LogEntryType.LOG_BIN)) {
			binCount++;
		    } else if (childType.equals(LogEntryType.LOG_DIN)) {
			dinCount++;
		    } else if (childType.equals(LogEntryType.LOG_IN)) {
			inCount++;
		    } else if (childType.equals(LogEntryType.LOG_LN)) {
			entryCount++;
		    } else if (childType.equals(LogEntryType.LOG_DUPCOUNTLN)) {
			dupLNCount++;
		    } else {
			throw new RuntimeException
			    ("unknown entry type: " + childType);
		    }
		}
	    };
	SortedLSNTreeWalker walker =
	    new SortedLSNTreeWalker(dbImpl, false, false,
                                    dbImpl.getTree().getRootLsn(), tnp,
                                    null); /* savedExceptions */

	walker.walk();

	if (DEBUG) {
	    System.out.println(stats);
	}

	/* Add one since the root LSN is not passed to the walker. */
	assertEquals(stats.getInternalNodeCount(), tnp.inCount + 1);
	assertEquals(stats.getBottomInternalNodeCount(), tnp.binCount);
	assertEquals(stats.getDuplicateInternalNodeCount(), tnp.dinCount);
	assertEquals(stats.getDuplicateBottomInternalNodeCount(),
		     tnp.dbinCount);
	assertEquals(stats.getLeafNodeCount(), tnp.entryCount);
	assertEquals(stats.getDupCountLeafNodeCount(), tnp.dupLNCount);
	if (DEBUG) {
	    System.out.println("INs: " + tnp.inCount);
	    System.out.println("BINs: " + tnp.binCount);
	    System.out.println("DINs: " + tnp.dinCount);
	    System.out.println("DBINs: " + tnp.dbinCount);
	    System.out.println("entries: " + tnp.entryCount);
	    System.out.println("dupLN: " + tnp.dupLNCount);
	}

	return tnp.entryCount;
    }

    private static class TestingTreeNodeProcessor
	implements TreeNodeProcessor {

	int binCount = 0;
	int dbinCount = 0;
	int dinCount = 0;
	int inCount = 0;
	int entryCount = 0;
        int dupLNCount = 0;

	public void processLSN(long childLSN, LogEntryType childType) {
	    throw new RuntimeException("override me please");
	}
    }

    private void close()
        throws Exception {

        if (db != null) {
            db.close();
            db = null;
        }

        if (env != null) {
            env.close();
            env = null;
        }
    } 
}
