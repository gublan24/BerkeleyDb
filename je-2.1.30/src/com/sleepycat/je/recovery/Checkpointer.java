/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Sleepycat Software.  All rights reserved.
 *
 * $Id: Checkpointer.java,v 1.128 2006/01/03 21:55:53 bostic Exp $
 */

package com.sleepycat.je.recovery;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;

import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.cleaner.Cleaner;
import com.sleepycat.je.cleaner.TrackedFileSummary;
import com.sleepycat.je.cleaner.UtilizationProfile;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.DbTree;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.INList;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.log.LogManager;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.ChildReference;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.Node;
import com.sleepycat.je.tree.SearchResult;
import com.sleepycat.je.tree.Tree;
import com.sleepycat.je.tree.WithRootLatched;
import com.sleepycat.je.utilint.DaemonThread;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.PropUtil;
import com.sleepycat.je.utilint.Tracer;

/**
 * The Checkpointer looks through the tree for internal nodes that must be
 * flushed to the log. Checkpoint flushes must be done in ascending order from
 * the bottom of the tree up.
 */
public class Checkpointer extends DaemonThread {

    private EnvironmentImpl envImpl;
    private LogManager logManager;

    /* Checkpoint sequence, initialized at recovery. */
    private long checkpointId;  

    /* 
     * How much the log should grow between checkpoints. If 0, we're using time
     * based checkpointing.
     */
    private long logSizeBytesInterval;
    private long logFileMax;
    private long timeInterval;
    private long lastCheckpointMillis;

    /* Stats */
    private int nCheckpoints;
    private long lastFirstActiveLsn;
    private long lastCheckpointStart;
    private long lastCheckpointEnd;

    private int nFullINFlush;
    private int nFullBINFlush;
    private int nDeltaINFlush;
    private int nFullINFlushThisRun;
    private int nDeltaINFlushThisRun;

    /* For future addition to stats:
    private int nAlreadyEvictedThisRun;
    */

    private volatile int highestFlushLevel;

    public Checkpointer(EnvironmentImpl envImpl,
                        long waitTime,
                        String name) 
        throws DatabaseException {

        super(waitTime, name, envImpl);
        this.envImpl = envImpl;
        logSizeBytesInterval = 
            envImpl.getConfigManager().getLong
                (EnvironmentParams.CHECKPOINTER_BYTES_INTERVAL);
        logFileMax = 
            envImpl.getConfigManager().getLong(EnvironmentParams.LOG_FILE_MAX);
        nCheckpoints = 0;
        timeInterval = waitTime;
        lastCheckpointMillis = 0;
	highestFlushLevel = IN.MIN_LEVEL;
        logManager = envImpl.getLogManager();
    }

    public int getHighestFlushLevel() {
	return highestFlushLevel;
    }

    /**
     * Figure out the wakeup period. Supplied through this static method
     * because we need to pass wakeup period to the superclass and need to do
     * the calcuation outside this constructor.
     */
    public static long getWakeupPeriod(DbConfigManager configManager) 
        throws IllegalArgumentException, DatabaseException {

        long wakeupPeriod = PropUtil.microsToMillis
            (configManager.getLong
                (EnvironmentParams.CHECKPOINTER_WAKEUP_INTERVAL));
        long bytePeriod = configManager.getLong
            (EnvironmentParams.CHECKPOINTER_BYTES_INTERVAL);

        /* Checkpointing period must be set either by time or by log size. */
        if ((wakeupPeriod == 0) && (bytePeriod == 0)) {
            throw new IllegalArgumentException
                (EnvironmentParams.CHECKPOINTER_BYTES_INTERVAL.getName() +
                 " and " +
                 EnvironmentParams.CHECKPOINTER_WAKEUP_INTERVAL.getName() +
                 " cannot both be 0. ");
        }

        /*
         * Checkpointing by log size takes precendence over time based period.
         */
        if (bytePeriod == 0) {
            return wakeupPeriod;
        } else {
            return 0;
        }
    }

    /**
     * Set checkpoint id -- can only be done after recovery.
     */
    synchronized public void setCheckpointId(long lastCheckpointId) {
        checkpointId = lastCheckpointId;
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("<Checkpointer name=\"").append(name).append("\"/>");
        return sb.toString();
    }

    /**
     * Load stats.
     */
    public void loadStats(StatsConfig config, EnvironmentStats stat) 
        throws DatabaseException {

        stat.setNCheckpoints(nCheckpoints);
        stat.setLastCheckpointStart(lastCheckpointStart);
        stat.setLastCheckpointEnd(lastCheckpointEnd);
        stat.setLastCheckpointId(checkpointId);
        stat.setNFullINFlush(nFullINFlush);
        stat.setNFullBINFlush(nFullBINFlush);
        stat.setNDeltaINFlush(nDeltaINFlush);
        
        if (config.getClear()) {
            nCheckpoints = 0;
            nFullINFlush = 0;
            nFullBINFlush = 0;
            nDeltaINFlush = 0;
        }
    }
    
    /**
     * @return the first active LSN point of the last completed checkpoint.
     * If no checkpoint has run, return null.
     */
    public long getFirstActiveLsn() {
        return lastFirstActiveLsn;
    }

    /**
     * Initialize the FirstActiveLsn during recovery.  The cleaner needs this.
     */
    public void setFirstActiveLsn(long lastFirstActiveLsn) {
        this.lastFirstActiveLsn = lastFirstActiveLsn;
    }

    synchronized public void clearEnv() {
        envImpl = null;
    }

    /**
     * Return the number of retries when a deadlock exception occurs.
     */
    protected int nDeadlockRetries()
        throws DatabaseException {

        return envImpl.getConfigManager().getInt
            (EnvironmentParams.CHECKPOINTER_RETRY);
    }

    /**
     * Called whenever the DaemonThread wakes up from a sleep.  
     */
    protected void onWakeup()
        throws DatabaseException {

        if (envImpl.isClosed()) {
            return;
        }

        doCheckpoint(CheckpointConfig.DEFAULT,
                     false, // flushAll
                     "daemon");
    }

    /**
     * Determine whether a checkpoint should be run.
     *
     * 1. If the force parameter is specified, always checkpoint. 
     *
     * 2. If the config object specifies time or log size, use that.
     *
     * 3. If the environment is configured to use log size based checkpointing,
     * check the log.
     *
     * 4. Lastly, use time based checking.
     */
    private boolean isRunnable(CheckpointConfig config)
        throws DatabaseException {

        /* Figure out if we're using log size or time to determine interval.*/
        long useBytesInterval = 0;
        long useTimeInterval = 0;
        long nextLsn = DbLsn.NULL_LSN;
        try {
            if (config.getForce()) {
                return true;
            } else if (config.getKBytes() != 0) {
                useBytesInterval = config.getKBytes() << 10;
            } else if (config.getMinutes() != 0) {
                // convert to millis
                useTimeInterval = config.getMinutes() * 60 * 1000;
            } else if (logSizeBytesInterval != 0) {
                useBytesInterval = logSizeBytesInterval;
            } else {
                useTimeInterval = timeInterval;
            }

            /* 
             * If our checkpoint interval is defined by log size, check on how
             * much log has grown since the last checkpoint.
             */
            if (useBytesInterval != 0) {
                nextLsn = envImpl.getFileManager().getNextLsn();
                if (DbLsn.getNoCleaningDistance(nextLsn, lastCheckpointEnd,
						logFileMax) >=
                    useBytesInterval) {
                    return true;
                } else {
                    return false;
                }
            } else if (useTimeInterval != 0) {

                /* 
                 * Our checkpoint is determined by time.  If enough time has
                 * passed and some log data has been written, do a checkpoint.
                 */
                long lastUsedLsn = envImpl.getFileManager().getLastUsedLsn();
                if (((System.currentTimeMillis() - lastCheckpointMillis) >=
                     useTimeInterval) &&
                    (DbLsn.compareTo(lastUsedLsn, lastCheckpointEnd) != 0)) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } finally {
            StringBuffer sb = new StringBuffer();
            sb.append("size interval=").append(useBytesInterval);
            if (nextLsn != DbLsn.NULL_LSN) {
                sb.append(" nextLsn=").
		    append(DbLsn.getNoFormatString(nextLsn));
            }
            if (lastCheckpointEnd != DbLsn.NULL_LSN) {
                sb.append(" lastCkpt=");
                sb.append(DbLsn.getNoFormatString(lastCheckpointEnd));
            }
            sb.append(" time interval=").append(useTimeInterval);
            sb.append(" force=").append(config.getForce());
            
            Tracer.trace(Level.FINEST,
                         envImpl, 
                         sb.toString());
        }
    }

    /**
     * The real work to do a checkpoint. This may be called by the checkpoint
     * thread when waking up, or it may be invoked programatically through the
     * api.
     * 
     * @param allowDeltas if true, this checkpoint may opt to log BIN deltas
     *       instead of the full node.
     * @param flushAll if true, this checkpoint must flush all the way to
     *       the top of the dbtree, instead of stopping at the highest level
     *       last modified.
     * @param invokingSource a debug aid, to indicate who invoked this 
     *       checkpoint. (i.e. recovery, the checkpointer daemon, the cleaner,
     *       programatically)
     */
    public synchronized void doCheckpoint(CheckpointConfig config,
					  boolean flushAll,
					  String invokingSource) 
        throws DatabaseException {

        if (envImpl.isReadOnly()) {
            return;
        }

	if (!isRunnable(config)) {
	    return;
	}

        /*
         * If there are cleaned files to be deleted, flush an extra level to
         * write out the parents of cleaned nodes.  This ensures that the node
         * will contain the LSN of a cleaned files.
         */
        boolean flushExtraLevel = false;
        Cleaner cleaner = envImpl.getCleaner();
        Set[] cleanerFiles = cleaner.getFilesAtCheckpointStart();
        if (cleanerFiles != null) {
            flushExtraLevel = true;
        }

        lastCheckpointMillis = System.currentTimeMillis();
        resetPerRunCounters();

        /* Get the next checkpoint id. */
        checkpointId++;
        nCheckpoints++;
        boolean success = false;
        boolean traced = false;
        int dirtyMapMemSize = 0;
        MemoryBudget mb = envImpl.getMemoryBudget();
        try {

	    /* 
	     * Eviction can run during checkpoint as long as it follows the
	     * same rules for using provisional logging and for propagating
	     * logging of the checkpoint dirty set up the tree. We have to lock
	     * out the evictor after the logging of checkpoint start until
	     * we've selected the dirty set and decided on the highest level to
	     * be flushed. See SR 11163, 11349.
	     */
	    long checkpointStart = DbLsn.NULL_LSN;
	    long firstActiveLsn = DbLsn.NULL_LSN;
	    SortedMap dirtyMap = null;
	    synchronized (envImpl.getEvictor()) {
		/* Log the checkpoint start. */
		CheckpointStart startEntry =
		    new CheckpointStart(checkpointId, invokingSource);
		checkpointStart = logManager.log(startEntry);

		/* 
		 * Remember the first active LSN -- before this position in the
		 * log, there are no active transactions at this point in time.
		 */
		firstActiveLsn = envImpl.getTxnManager().getFirstActiveLsn();

		if (firstActiveLsn == DbLsn.NULL_LSN) {
		    firstActiveLsn = checkpointStart;
		} else {
		    if (DbLsn.compareTo(checkpointStart, firstActiveLsn) < 0) {
			firstActiveLsn = checkpointStart;
		    }
		}
		/* Find the dirty set. */
		dirtyMap = selectDirtyINs(flushAll, flushExtraLevel);
	    }

            /* Add each level's references to the budget. */
	    int totalSize = 0;
            for (Iterator i = dirtyMap.values().iterator(); i.hasNext();) {
                Set nodeSet = (Set) i.next();
                int size = nodeSet.size() *
                    MemoryBudget.CHECKPOINT_REFERENCE_SIZE;
		totalSize += size;
                dirtyMapMemSize += size;
            }
	    mb.updateMiscMemoryUsage(totalSize);

            /* Flush IN nodes. */
            boolean allowDeltas = !config.getMinimizeRecoveryTime();
            flushDirtyNodes(dirtyMap, flushAll, allowDeltas,
                            flushExtraLevel, checkpointStart);

            /*
             * Flush utilization info AFTER flushing IN nodes to reduce the
             * inaccuracies caused by the sequence FileSummaryLN-LN-BIN.
             */
            flushUtilizationInfo();

            CheckpointEnd endEntry =
                new CheckpointEnd(invokingSource,
                                  checkpointStart,
                                  envImpl.getRootLsn(),
                                  firstActiveLsn,
                                  Node.getLastId(),
                                  envImpl.getDbMapTree().getLastDbId(),
                                  envImpl.getTxnManager().getLastTxnId(),
                                  checkpointId);

            /* 
             * Log checkpoint end and update state kept about the last
             * checkpoint location. Send a trace message *before* the
             * checkpoint end log entry. This is done so that the normal trace
             * message doesn't affect the time-based isRunnable() calculation,
             * which only issues a checkpoint if a log record has been written
             * since the last checkpoint.
             */
            trace(envImpl, invokingSource, true);
            traced = true;

            /*
             * Always flush to ensure that cleaned files are not referenced,
             * and to ensure that this checkpoint is not wasted if we crash.
             */
            lastCheckpointEnd = 
            	logManager.logForceFlush(endEntry,
            	                 	 true); // fsync required
            lastFirstActiveLsn = firstActiveLsn;
            lastCheckpointStart = checkpointStart;

	    /* 
	     * Reset the highestFlushLevel so evictor activity knows there's no
	     * further requirement for provisional logging. SR 11163.
	     */
	    highestFlushLevel = IN.MIN_LEVEL;

            success = true;

            if (cleanerFiles != null) {
                cleaner.updateFilesAtCheckpointEnd(cleanerFiles);
            }
        } catch (DatabaseException e) {
            Tracer.trace(envImpl, "Checkpointer", "doCheckpoint",
                         "checkpointId=" + checkpointId, e);
            throw e;
        } finally {
            mb.updateMiscMemoryUsage(0 - dirtyMapMemSize);
            if (!traced) {
                trace(envImpl, invokingSource, success);
            }
        }
    }

    private void trace(EnvironmentImpl envImpl,
                       String invokingSource, boolean success ) {
        StringBuffer sb = new StringBuffer();
        sb.append("Checkpoint ").append(checkpointId);
        sb.append(": source=" ).append(invokingSource);
        sb.append(" success=").append(success);
        sb.append(" nFullINFlushThisRun=").append(nFullINFlushThisRun);
        sb.append(" nDeltaINFlushThisRun=").append(nDeltaINFlushThisRun);
        Tracer.trace(Level.CONFIG, envImpl, sb.toString());
    }

    /**
     * Flush a FileSummaryLN node for each TrackedFileSummary that is currently
     * active.  Tell the UtilizationProfile about the updated file summary.
     */
    private void flushUtilizationInfo()
        throws DatabaseException {

        /* Utilization flushing may be disabled for unittests. */
        if (!DbInternal.getCheckpointUP
	    (envImpl.getConfigManager().getEnvironmentConfig())) {
            return;
        }
        
        UtilizationProfile profile = envImpl.getUtilizationProfile();

        TrackedFileSummary[] activeFiles =
            envImpl.getUtilizationTracker().getTrackedFiles();

        for (int i = 0; i < activeFiles.length; i += 1) {
            profile.flushFileSummary(activeFiles[i]);
        }
    }

    /**
     * Flush the nodes in order, from the lowest level to highest level.  As a
     * flush dirties its parent, add it to the dirty map, thereby cascading the
     * writes up the tree. If flushAll wasn't specified, we need only cascade
     * up to the highest level set at the start of checkpointing.
     *
     * Note that all but the top level INs and the BINDeltas are logged
     * provisionally. That's because we don't need to process lower INs because
     * the higher INs will end up pointing at them.
     */
    private void flushDirtyNodes(SortedMap dirtyMap,
				 boolean flushAll,
				 boolean allowDeltas,
                                 boolean flushExtraLevel,
                                 long checkpointStart)
        throws DatabaseException {

        while (dirtyMap.size() > 0) {

            /* Work on one level's worth of nodes in ascending level order. */
            Integer currentLevel = (Integer) dirtyMap.firstKey();
            boolean logProvisionally =
                (currentLevel.intValue() != highestFlushLevel);

            Set nodeSet = (Set) dirtyMap.get(currentLevel);
            Iterator iter = nodeSet.iterator();

            /* Flush all those nodes */
            while (iter.hasNext()) {
                CheckpointReference targetRef =
                    (CheckpointReference) iter.next();

                /* Evict before each operation. */
                envImpl.getEvictor().doCriticalEviction();

                /* 
                 * Check if the db is still valid since INs of deleted
                 * databases are left on the in-memory tree until the post
                 * transaction cleanup is finished.
                 */
                if (!(targetRef.db.isDeleted())) {
                    flushIN(targetRef, dirtyMap, currentLevel.intValue(),
                            logProvisionally, allowDeltas, checkpointStart);
                }
                
                iter.remove();
            }

            /* We're done with this level. */
            dirtyMap.remove(currentLevel);

            /* We can stop at this point. */
            if (currentLevel.intValue() == highestFlushLevel) {
                break;
            }
        }
    }

    /**
     * Scan the INList for all dirty INs. Arrange them in level sorted map for
     * level ordered flushing.
     */
    private SortedMap selectDirtyINs(boolean flushAll,
                                     boolean flushExtraLevel) 
        throws DatabaseException {

        SortedMap newDirtyMap = new TreeMap();

        INList inMemINs = envImpl.getInMemoryINs();
        inMemINs.latchMajor();

        /* 
	 * Opportunistically recalculate the environment wide memory count.
	 * Incurs no extra cost because we're walking the IN list anyway.  Not
	 * the best in terms of encapsulation as prefereably all memory
	 * calculations are done in MemoryBudget, but done this way to avoid
	 * any extra latching.
	 */
        long totalSize = 0;
        MemoryBudget mb = envImpl.getMemoryBudget();

        try {
            Iterator iter = inMemINs.iterator();
            while (iter.hasNext()) {
                IN in = (IN) iter.next();
                in.latch(false);
                try {
                    totalSize = mb.accumulateNewUsage(in, totalSize);

                    if (in.getDirty()) {
                        Integer level = new Integer(in.getLevel());
                        Set dirtySet;
                        if (newDirtyMap.containsKey(level)) {
                            dirtySet = (Set) newDirtyMap.get(level);
                        } else {
                            dirtySet = new HashSet();
                            newDirtyMap.put(level, dirtySet);
                        }
                        dirtySet.add
                             (new CheckpointReference(in.getDatabase(),
                                                      in.getNodeId(),
                                                      in.containsDuplicates(),
                                                      in.isDbRoot(),
                                                      in.getMainTreeKey(),
                                                      in.getDupTreeKey()));
                    }
                } finally {
                    in.releaseLatch();
                }
            }

            /* Set the tree cache size. */
            mb.refreshTreeMemoryUsage(totalSize);

            /* 
             * If we're flushing all for cleaning, we must flush to the point
             * that there are no nodes with LSNs in the cleaned files. 
             */
            if (newDirtyMap.size() > 0) {
                if (flushAll) {
                    highestFlushLevel =
			envImpl.getDbMapTree().getHighestLevel();
                } else {
                    highestFlushLevel =
                        ((Integer) newDirtyMap.lastKey()).intValue();
                    if (flushExtraLevel) {
                        highestFlushLevel += 1;
                    }
                }
            } else {
		highestFlushLevel = IN.MAX_LEVEL;
	    }
        } finally {
            inMemINs.releaseMajorLatchIfHeld();
        }

        return newDirtyMap;
    }
    
    /** 
     * Flush the target IN.
     */
    private void flushIN(CheckpointReference targetRef,
                         Map dirtyMap,
                         int currentLevel,
                         boolean logProvisionally,
                         boolean allowDeltas,
                         long checkpointStart)
        throws DatabaseException {

        Tree tree = targetRef.db.getTree();
        boolean targetWasRoot = false;
        if (targetRef.isDbRoot) {
            /* We're trying to flush the root. */
            RootFlusher flusher =
		new RootFlusher(targetRef.db, logManager, targetRef.nodeId);
            tree.withRootLatchedExclusive(flusher);
            boolean flushed = flusher.getFlushed();

            /* 
             * If this target isn't the root anymore, we'll have to handle it
             * like a regular node.
             */
            targetWasRoot = flusher.stillRoot();
            
            /* 
             * Update the tree's owner, whether it's the env root or the
             * dbmapping tree.
             */
            if (flushed) {
                DbTree dbTree = targetRef.db.getDbEnvironment().getDbMapTree();
                dbTree.modifyDbRoot(targetRef.db);
                nFullINFlushThisRun++;
                nFullINFlush++;
            }
        }

        /* 
         * The following attempt to flush applies to two cases:
	 * 
         * (1) the target was not ever the root
	 * 
         * (2) the target was the root, when the checkpoint dirty set was 
         * assembled but is not the root now.
	 * 
         */
        if (!targetWasRoot) {

            /*
             * The "isRoot" param is used to stop a search in
             * BIN.descendOnParentSearch and is passed as false (never stop).
             */ 
            SearchResult result =
                tree.getParentINForChildIN(targetRef.nodeId,
                                           targetRef.containsDuplicates,
                                           false,  // isRoot
                                           targetRef.mainTreeKey,
                                           targetRef.dupTreeKey,
                                           false,  // requireExactMatch
                                           false,  // updateGeneration
                                           -1,     // targetLevel
                                           null,   // trackingList
                                           false); // doFetch

            /* 
             * We must make sure that every IN that was selected for the
             * checkpointer's dirty IN set at the beginning of checkpoint is
             * written into the log and can be properly accessed from
             * ancestors. However, we have to take care for cases where the
             * evictor has written out a member of this dirty set before the
             * checkpointer got to it. See SR 10249.
             *
             * If no possible parent is found, the compressor may have deleted
             * this item before we got to processing it.
             */
            if (result.parent != null) {
                boolean mustLogParent = false;
                try {
                    if (result.exactParentFound) {

                        /* 
                         * If the child has already been evicted, don't
                         * refetch it.
                         */
                        IN renewedTarget =
                            (IN) result.parent.getTarget(result.index);

                        if (renewedTarget == null) {
                            /* nAlreadyEvictedThisRun++;  -- for future */
                            mustLogParent = true;
                        } else {
                            mustLogParent =
                                logTargetAndUpdateParent(renewedTarget,
                                                         result.parent,
                                                         result.index,
                                                         allowDeltas,
                                                         checkpointStart,
                                                         logProvisionally);
                        }
                    } else {
                        /* result.exactParentFound was false. */
                        if (result.childNotResident) {

                            /* 
                             * But it was because the child wasn't resident.
                             * To be on the safe side, we'll put the parent
                             * into the dirty set to be logged when that level
                             * is processed.
                             *
                             * Only do this if the parent we found is at a
                             * higher level than the child.  This ensures that
                             * the non-exact search does not find a sibling
                             * rather than a parent. [#11555]
                             */
                            if (result.parent.getLevel() > currentLevel) {
                                mustLogParent = true;
                            }
                            /* nAlreadyEvictedThisRun++; -- for future. */
                        }
                    }

                    if (mustLogParent) {
                        assert
                            checkParentChildRelationship(result, currentLevel):
                            dumpParentChildInfo(result,
                                                result.parent,
                                                targetRef.nodeId,
                                                currentLevel,
                                                tree);

                        addToDirtyMap(dirtyMap, result.parent);
                    }
                } finally {
                    result.parent.releaseLatch();
                }
            }
        }
    }
    
    /** 
     * @return true if this parent is appropriately 1 level above the child.
     */
    private boolean checkParentChildRelationship(SearchResult result,
                                                 int childLevel) {

        if (result.childNotResident && !result.exactParentFound) {

            /* 
             * This might be coming from the #11555 clause, in which case we
             * are logging over-cautiously, but intentionally, and the levels
             * might not pass the test below.
             */
            return true;
        }

        /* 
         * In the main tree or mapping tree, your parent must be in the same
         * number space, and must be 1 more than the child.  In the dup tree,
         * the parent might be a BIN from the main tree.
         */
        int parentLevel = result.parent.getLevel();
        boolean isMapTree = (childLevel & IN.DBMAP_LEVEL) != 0;
        boolean isMainTree = (childLevel & IN.MAIN_LEVEL) != 0;

        boolean checkOk = false;
        if (isMapTree || isMainTree) {
            /* The parent must be child level + 1 */
            if (parentLevel == (childLevel + 1)) {
                checkOk = true;
            }
        } else {
            if (childLevel == 1) { 
                /* A DBIN must have a level 2 DIN parent. */
                if (parentLevel == 2) {
                    checkOk = true;
                }
            } else {
                /* A DIN must have either a BIN or DIN parent. */
                if ((parentLevel == IN.BIN_LEVEL)  ||
                    (parentLevel == childLevel + 1)) {
                    checkOk = true;
                }
            }
        }
        return checkOk;
    }

    private String dumpParentChildInfo(SearchResult result,
                                       IN parent,
                                       long childNodeId,
                                       int currentLevel,
                                       Tree tree) 
        throws DatabaseException {

        StringBuffer sb = new StringBuffer();
        sb.append("ckptId=").append(checkpointId);
        sb.append(" result=").append(result);
        sb.append(" parent node=").append(parent.getNodeId());
        sb.append(" level=").append(parent.getLevel());
        sb.append(" child node=").append(childNodeId);
        sb.append(" level=").append(currentLevel);
        return sb.toString();
    }

    private boolean logTargetAndUpdateParent(IN target,
                                             IN parent,
                                             int index,
                                             boolean allowDeltas,
                                             long checkpointStart,
                                             boolean logProvisionally) 
        throws DatabaseException {

        target.latch(false);
        long newLsn = DbLsn.NULL_LSN;
        boolean mustLogParent = true;
        try {

            /* 
             * Compress this node if necessary. Note that this may dirty the
             * node.
             */
            envImpl.lazyCompress(target);

            if (target.getDirty()) {

                /* 
                 * Note that target decides whether to log a delta. Only BINs
                 * that fall into the required percentages and have not been
                 * cleaned will be logged with a delta.  Cleaner migration is
                 * allowed.
                 */
                newLsn = target.log(logManager,
                                    allowDeltas,
                                    logProvisionally,
                                    true,  // proactiveMigration
                                    parent);
                if (allowDeltas && newLsn == DbLsn.NULL_LSN) {
                    nDeltaINFlushThisRun++;
                    nDeltaINFlush++;

                    /* 
                     * If this BIN was already logged after checkpoint start
                     * and before this point (i.e. by an eviction), we must
                     * make sure that the last full version is accessible from
                     * ancestors. We can skip logging parents only if this is
                     * the first logging of this node in the checkpoint
                     * interval.
                     */
                    long lastFullLsn =  target.getLastFullVersion();
                    if (DbLsn.compareTo(lastFullLsn,
                                        checkpointStart) < 0) {
                        mustLogParent = false;
                    }
                }
            }
        } finally {
            target.releaseLatch();
        }

        /* Update the parent if a full version was logged. */
        if (newLsn != DbLsn.NULL_LSN) {
            nFullINFlushThisRun++;
            nFullINFlush++;
            if (target instanceof BIN) {
                nFullBINFlush++;
            }
            parent.updateEntry(index, newLsn);
        }
        
        return mustLogParent;
    }

    /*
     * RootFlusher lets us write out the root IN within the root latch.
     */
    private static class RootFlusher implements WithRootLatched {
        private DatabaseImpl db;
        private boolean flushed;
        private boolean stillRoot;
        private LogManager logManager;
        private long targetNodeId;

        RootFlusher(DatabaseImpl db,
                    LogManager logManager,
                    long targetNodeId) {
            this.db = db;
            flushed = false;
            this.logManager = logManager;
            this.targetNodeId = targetNodeId;
            stillRoot = false;
        }

        /**
         * Flush the rootIN if dirty.
         */
        public IN doWork(ChildReference root) 
            throws DatabaseException {

	    if (root == null) {
		return null;
	    }
            IN rootIN = (IN) root.fetchTarget(db, null);
            rootIN.latch(false);
            try {
                if (rootIN.getNodeId() == targetNodeId) {

                    /* 
		     * stillRoot handles the situation where the root was split
		     * after it was placed in the checkpointer's dirty set.
                     */
                    stillRoot = true;
                    if (rootIN.getDirty()) {
                        long newLsn = rootIN.log(logManager);
                        root.setLsn(newLsn);
                        flushed = true;
                    }
                }
            } finally {
                rootIN.releaseLatch();
            }                    
            return null;
        }

        boolean getFlushed() {
            return flushed;
        }

        boolean stillRoot() {
            return stillRoot;
        }
    }

    /**
     * Add a node to the dirty map. The dirty map is keyed by level (Integers)
     * and holds sets of IN references.
     */
    private void addToDirtyMap(Map dirtyMap, IN in) {
        Integer inLevel = new Integer(in.getLevel());
        Set inSet = (Set) dirtyMap.get(inLevel);
        
        /* If this level doesn't exist in the map yet, make a new entry. */
        if (inSet == null) {
            inSet = new HashSet();
            dirtyMap.put(inLevel, inSet);
        }    
        
        /* Add to the set. */
        inSet.add(new CheckpointReference(in.getDatabase(),
                                          in.getNodeId(),
                                          in.containsDuplicates(),
                                          in.isDbRoot(),
                                          in.getMainTreeKey(),
                                          in.getDupTreeKey()));
    }

    /**
     * Reset per-run counters.
     */
    private void resetPerRunCounters() {
        nFullINFlushThisRun = 0;
        nDeltaINFlushThisRun = 0;
        /* nAlreadyEvictedThisRun = 0; -- for future */
    }

    /* 
     * CheckpointReferences are used to identify nodes that must be flushed as
     * part of the checkpoint. We don't keep an actual reference to the node
     * because that prevents nodes from being GC'ed during checkpoint.
     *
     * Using a checkpointReference introduces a window between the point when
     * the checkpoint dirty set is created and when the node is flushed. Some
     * of the fields saved in the reference are immutable: db, nodeId,
     * containsDuplicates. The others are not and we have to handle potential
     * change:
     *
     * isDbRoot: it's possible for isDbRoot to go from true->false, but not 
     *         false->true. True->false is handled by the flushIN method
     *         by finding the root and checking if it is the target.
     * mainTreeKey, dupTreeKey: These can change only in the event of a 
     *         split. If they do, there is the chance that the checkpointer
     *         will find the wrong node to flush, but that's okay because
     *         the split guarantees flushing to the root, so the target will
     *         be properly logged within the checkpoint period.
     *
     * The class and ctor are public for the Sizeof program.
     */
    public static class CheckpointReference {
        DatabaseImpl db;
        long nodeId;
        boolean containsDuplicates;
        boolean isDbRoot;
        byte[] mainTreeKey;
        byte[] dupTreeKey;

        public CheckpointReference(DatabaseImpl db,
                            long nodeId,
                            boolean containsDuplicates,
                            boolean isDbRoot,
                            byte[] mainTreeKey,
                            byte[] dupTreeKey) {
            this.db = db;
            this.nodeId = nodeId;
            this.containsDuplicates = containsDuplicates;
            this.isDbRoot = isDbRoot;
            this.mainTreeKey = mainTreeKey;
            this.dupTreeKey = dupTreeKey;
        }

        public boolean equals(Object o) {
            if (!(o instanceof CheckpointReference)) {
                return false;
            }

            CheckpointReference other = (CheckpointReference) o;
            return nodeId == other.nodeId;
        }

        public int hashCode() {
            return (int) nodeId;
        }
    }
}
