/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Sleepycat Software.  All rights reserved.
 *
 * $Id: EnvironmentImpl.java,v 1.1 2006/05/06 09:00:25 ckaestne Exp $
 */

package com.sleepycat.je.dbi;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.EnvironmentMutableConfig;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.RunRecoveryException;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;
import com.sleepycat.je.TransactionStats;
import com.sleepycat.je.VerifyConfig;
import com.sleepycat.je.cleaner.Cleaner;
import com.sleepycat.je.cleaner.UtilizationProfile;
import com.sleepycat.je.cleaner.UtilizationTracker;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.evictor.Evictor;
import com.sleepycat.je.incomp.INCompressor;
import com.sleepycat.je.latch.Latch;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.latch.SharedLatch;
import com.sleepycat.je.log.FileManager;
import com.sleepycat.je.log.LatchedLogManager;
import com.sleepycat.je.log.LogManager;
import com.sleepycat.je.log.SyncedLogManager;
import com.sleepycat.je.log.TraceLogHandler;
import com.sleepycat.je.recovery.Checkpointer;
import com.sleepycat.je.recovery.RecoveryInfo;
import com.sleepycat.je.recovery.RecoveryManager;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.tree.BINReference;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.Key;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.txn.Txn;
import com.sleepycat.je.txn.TxnManager;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.PropUtil;
import com.sleepycat.je.utilint.Tracer;

/**
 * Underlying Environment implementation. There is a single instance for any
 * database environment opened by the application.
 */
public class EnvironmentImpl implements EnvConfigObserver {

	/*
	 * Set true and run unit tests for NO_LOCKING_MODE test.
	 * EnvironmentConfigTest.testInconsistentParams will fail. [#13788]
	 */
	private static final boolean TEST_NO_LOCKING_MODE = false;

	/* Attributes of the entire environment */
	private DbEnvState envState;

	private boolean closing; // true if close has begun

	private File envHome;

	private int referenceCount; // count of opened Database and DbTxns

	private boolean isTransactional; // true if env opened with DB_INIT_TRANS

	private boolean isNoLocking; // true if env has no locking

	private boolean isReadOnly; // true if env opened with the read only flag.

	private static boolean fairLatches;// true if user wants fair latches

	private MemoryBudget memoryBudget;

	/* Save so we don't have to look it up in the config manager frequently. */
	private long lockTimeout;

	private long txnTimeout;

	/* DatabaseImpl */
	private DbTree dbMapTree;

	private long mapTreeRootLsn = DbLsn.NULL_LSN;

	private Latch mapTreeRootLatch;

	private INList inMemoryINs;

	/* Services */
	private DbConfigManager configManager;

	private List configObservers;

	private Logger envLogger;

	protected LogManager logManager;

	private FileManager fileManager;

	private TxnManager txnManager;

	/* Daemons */
	private Evictor evictor;

	private INCompressor inCompressor;

	private Checkpointer checkpointer;

	private Cleaner cleaner;

	/* Stats, debug information */
	private RecoveryInfo lastRecoveryInfo;

	private RunRecoveryException savedInvalidatingException;

	/* If true, call Thread.yield() at strategic points (stress test aid) */
	private static boolean forcedYield = false;

	/*
	 * Used by Database to protect access to the trigger list. A single latch
	 * for all databases is used to prevent deadlocks.
	 */
	private SharedLatch triggerLatch;

	/*
	 * ThreadLocal.get() is not cheap so we want to minimize calls to it. We
	 * only use ThreadLocals for the TreeStatsAccumulator which are only called
	 * in limited circumstances. Use this reference count to indicate that a
	 * thread has set a TreeStatsAccumulator. When it's done, it decrements the
	 * counter. It's static so that we don't have to pass around the
	 * EnvironmentImpl.
	 */
	private static int threadLocalReferenceCount = 0;

	/**
	 * DbPrintLog doesn't need btree and dup comparators to function properly
	 * don't require any instantiations. This flag, if true, indicates that
	 * we've been called from DbPrintLog.
	 */
	private static boolean noComparators = false;

	public static final boolean JAVA5_AVAILABLE;

	private static final String DISABLE_JAVA_ADLER32 = "je.disable.java.adler32";

	static {
		boolean ret = false;
		if (System.getProperty(DISABLE_JAVA_ADLER32) == null) {

			/*
			 * Use this to determine if we're in J5.
			 */
			String javaVersion = System.getProperty("java.version");
			if (javaVersion != null && !javaVersion.startsWith("1.4.")) {
				ret = true;
			}
		}
		JAVA5_AVAILABLE = ret;
	}

	/**
	 * Create a database environment to represent the data in envHome. dbHome.
	 * Properties from the je.properties file in that directory are used to
	 * initialize the system wide property bag. Properties passed to this method
	 * are used to influence the open itself.
	 * 
	 * @param envHome
	 *            absolute path of the database environment home directory
	 * 
	 * @param envConfig
	 * 
	 * @throws DatabaseException
	 *             on all other failures
	 */
	public EnvironmentImpl(File envHome, EnvironmentConfig envConfig)
			throws DatabaseException {

		try {
			this.envHome = envHome;
			envState = DbEnvState.INIT;
			mapTreeRootLatch = LatchSupport.makeLatch("MapTreeRoot", this);

			/* Set up configuration parameters */
			configManager = new DbConfigManager(envConfig);
			configObservers = new ArrayList();
			addConfigObserver(this);

			/*
			 * Decide on memory budgets based on environment config params and
			 * memory available to this process.
			 */
			memoryBudget = new MemoryBudget(this, configManager);

			/*
			 * Set up debug logging. Depending on configuration, add handlers,
			 * set logging level.
			 */
			envLogger = initLogger(envHome);

			/*
			 * Essential services. These must exist before recovery.
			 */
			forcedYield = configManager
					.getBoolean(EnvironmentParams.ENV_FORCED_YIELD);
			isTransactional = configManager
					.getBoolean(EnvironmentParams.ENV_INIT_TXN);
			isNoLocking = !(configManager
					.getBoolean(EnvironmentParams.ENV_INIT_LOCKING));
			if (isTransactional && isNoLocking) {
				if (TEST_NO_LOCKING_MODE) {
					isNoLocking = !isTransactional;
				} else {
					throw new IllegalArgumentException(
							"Can't set 'je.env.isNoLocking' and "
									+ "'je.env.isTransactional';");
				}
			}
			fairLatches = configManager
					.getBoolean(EnvironmentParams.ENV_FAIR_LATCHES);
			isReadOnly = configManager.getBoolean(EnvironmentParams.ENV_RDONLY);

			fileManager = new FileManager(this, envHome, isReadOnly);
			if (!envConfig.getAllowCreate() && !fileManager.filesExist()) {
				throw new DatabaseException(
						"Enviroment creation isn't allowed, "
								+ " but there is no pre-existing "
								+ " environment in " + envHome);
			}

			if (fairLatches) {
				logManager = new LatchedLogManager(this, isReadOnly);
			} else {
				logManager = new SyncedLogManager(this, isReadOnly);
			}

			inMemoryINs = new INList(this);
			txnManager = new TxnManager(this);

			/*
			 * Daemons are always made here, but only started after recovery. We
			 * want them to exist so we can call them programatically even if
			 * the daemon thread is not started.
			 */
			createDaemons();

			/*
			 * Recovery will recreate the dbMapTree from the log if it exists.
			 */
			dbMapTree = new DbTree(this);

			referenceCount = 0;

			triggerLatch = LatchSupport.makeSharedLatch("TriggerLatch", this);

			/*
			 * Do not do recovery and start daemons if this environment is for a
			 * utility.
			 */
			if (configManager.getBoolean(EnvironmentParams.ENV_RECOVERY)) {

				/*
				 * Run recovery. Note that debug logging to the database log is
				 * disabled until recovery is finished.
				 */
				try {
					RecoveryManager recoveryManager = new RecoveryManager(this);
					lastRecoveryInfo = recoveryManager.recover(isReadOnly);
				} finally {
					try {
						/* Flush to get all exception tracing out to the log. */
						logManager.flush();
						fileManager.clear();
					} catch (IOException e) {
						throw new DatabaseException(e.getMessage());
					}
				}
			} else {
				isReadOnly = true;
				noComparators = true;
			}

			/* Start daemons after recovery. */
			runOrPauseDaemons(configManager);

			/*
			 * Cache a few critical values. We keep our timeout in millis
			 * instead of microseconds because Object.wait takes millis.
			 */
			lockTimeout = PropUtil.microsToMillis(configManager
					.getLong(EnvironmentParams.LOCK_TIMEOUT));
			txnTimeout = PropUtil.microsToMillis(configManager
					.getLong(EnvironmentParams.TXN_TIMEOUT));

			/* Initialize the environment memory usage number. */
			memoryBudget.initCacheMemoryUsage();

			/* Mark as open. */
			open();
		} catch (DatabaseException e) {

			/* Release any environment locks if there was a problem. */
			if (fileManager != null) {
				try {
					fileManager.close();
				} catch (IOException IOE) {

					/*
					 * Klockwork - ok Eat it, we want to throw the original
					 * exception.
					 */
				}
			}
			throw e;
		}
	}

	/**
	 * Respond to config updates.
	 */
	public void envConfigUpdate(DbConfigManager mgr) throws DatabaseException {

		/* For now only daemon run properties are mutable. */
		runOrPauseDaemons(mgr);
	}

	/**
	 * Read configurations for daemons, instantiate.
	 */
	private void createDaemons() throws DatabaseException {

		/* Evictor */
		evictor = new Evictor(this, "Evictor");

		/* Checkpointer */

		/*
		 * Make sure that either log-size-based or time-based checkpointing is
		 * enabled.
		 */
		long checkpointerWakeupTime = Checkpointer
				.getWakeupPeriod(configManager);
		checkpointer = new Checkpointer(this, checkpointerWakeupTime,
				"Checkpointer");

		/* INCompressor */
		long compressorWakeupInterval = PropUtil.microsToMillis(configManager
				.getLong(EnvironmentParams.COMPRESSOR_WAKEUP_INTERVAL));
		inCompressor = new INCompressor(this, compressorWakeupInterval,
				"INCompressor");

		/* The cleaner is not time-based so no wakeup interval is used. */
		cleaner = new Cleaner(this, "Cleaner");
	}

	/**
	 * Run or pause daemons, depending on config properties.
	 */
	private void runOrPauseDaemons(DbConfigManager mgr)
			throws DatabaseException {

		if (!isReadOnly) {
			/* INCompressor */
			inCompressor.runOrPause(mgr
					.getBoolean(EnvironmentParams.ENV_RUN_INCOMPRESSOR));

			/* Cleaner. Do not start it if running in-memory */
			cleaner.runOrPause(mgr
					.getBoolean(EnvironmentParams.ENV_RUN_CLEANER)
					&& !mgr.getBoolean(EnvironmentParams.LOG_MEMORY_ONLY));

			/*
			 * Checkpointer. Run in both transactional and non-transactional
			 * environments to guarantee recovery time.
			 */
			checkpointer.runOrPause(mgr
					.getBoolean(EnvironmentParams.ENV_RUN_CHECKPOINTER));
		}

		/* Evictor */
		evictor.runOrPause(mgr.getBoolean(EnvironmentParams.ENV_RUN_EVICTOR));
	}

	/**
	 * Return the incompressor. In general, don't use this directly because it's
	 * easy to forget that the incompressor can be null at times (i.e during the
	 * shutdown procedure. Instead, wrap the functionality within this class,
	 * like lazyCompress.
	 */
	public INCompressor getINCompressor() {
		return inCompressor;
	}

	/**
	 * Returns the UtilizationTracker.
	 */
	public UtilizationTracker getUtilizationTracker() {
		return cleaner.getUtilizationTracker();
	}

	/**
	 * Returns the UtilizationProfile.
	 */
	public UtilizationProfile getUtilizationProfile() {
		return cleaner.getUtilizationProfile();
	}

	/**
	 * Log the map tree root and save the LSN.
	 */
	public void logMapTreeRoot() throws DatabaseException {

		mapTreeRootLatch.acquire();
		try {
			mapTreeRootLsn = logManager.log(dbMapTree);
		} finally {
			mapTreeRootLatch.release();
		}
	}

	/**
	 * Force a rewrite of the map tree root if required.
	 */
	public void rewriteMapTreeRoot(long cleanerTargetLsn)
			throws DatabaseException {

		mapTreeRootLatch.acquire();
		try {
			if (DbLsn.compareTo(cleanerTargetLsn, mapTreeRootLsn) == 0) {

				/*
				 * The root entry targetted for cleaning is in use. Write a new
				 * copy.
				 */
				mapTreeRootLsn = logManager.log(dbMapTree);
			}
		} finally {
			mapTreeRootLatch.release();
		}
	}

	/**
	 * @return the mapping tree root LSN.
	 */
	public long getRootLsn() {
		return mapTreeRootLsn;
	}

	/**
	 * Set the mapping tree from the log. Called during recovery.
	 */
	public void readMapTreeFromLog(long rootLsn) throws DatabaseException {

		dbMapTree = (DbTree) logManager.get(rootLsn);
		dbMapTree.setEnvironmentImpl(this);

		/* Set the map tree root */
		mapTreeRootLatch.acquire();
		try {
			mapTreeRootLsn = rootLsn;
		} finally {
			mapTreeRootLatch.release();
		}
	}

	/**
	 * Tells the asynchronous IN compressor thread about a BIN with a deleted
	 * entry.
	 */
	public void addToCompressorQueue(BIN bin, Key deletedKey, boolean doWakeup)
			throws DatabaseException {

		/*
		 * May be called by the cleaner on its last cycle, after the compressor
		 * is shut down.
		 */
		if (inCompressor != null) {
			inCompressor.addBinKeyToQueue(bin, deletedKey, doWakeup);
		}
	}

	/**
	 * Tells the asynchronous IN compressor thread about a BINReference with a
	 * deleted entry.
	 */
	public void addToCompressorQueue(BINReference binRef, boolean doWakeup)
			throws DatabaseException {

		/*
		 * May be called by the cleaner on its last cycle, after the compressor
		 * is shut down.
		 */
		if (inCompressor != null) {
			inCompressor.addBinRefToQueue(binRef, doWakeup);
		}
	}

	/**
	 * Tells the asynchronous IN compressor thread about a collections of
	 * BINReferences with deleted entries.
	 */
	public void addToCompressorQueue(Collection binRefs, boolean doWakeup)
			throws DatabaseException {

		/*
		 * May be called by the cleaner on its last cycle, after the compressor
		 * is shut down.
		 */
		if (inCompressor != null) {
			inCompressor.addMultipleBinRefsToQueue(binRefs, doWakeup);
		}
	}

	/**
	 * Do lazy compression at opportune moments.
	 */
	public void lazyCompress(IN in) throws DatabaseException {

		/*
		 * May be called by the cleaner on its last cycle, after the compressor
		 * is shut down.
		 */
		if (inCompressor != null) {
			inCompressor.lazyCompress(in);
		}
	}

	/**
	 * Initialize the debugging logging system. Note that publishing to the
	 * database log is not permitted until we've initialized the file manager in
	 * recovery. We can't log safely before that.
	 */
	private Logger initLogger(File envHome) throws DatabaseException {

		/*
		 * XXX, this creates problems in unit tests, not sure why yet Logger
		 * logger = Logger.getLogger(EnvironmentImpl.class.getName() + "." +
		 * envNum);
		 */
		Logger logger = Logger.getAnonymousLogger();

		/*
		 * Disable handlers inherited from parents, we want JE to control its
		 * own behavior. Add our handlers based on configuration
		 */
		logger.setUseParentHandlers(false);

		/* Set the logging level. */
		Level level = Tracer.parseLevel(this,
				EnvironmentParams.JE_LOGGING_LEVEL);
		logger.setLevel(level);

		/* Log to console. */
		if (configManager.getBoolean(EnvironmentParams.JE_LOGGING_CONSOLE)) {
			Handler consoleHandler = new ConsoleHandler();
			consoleHandler.setLevel(level);
			logger.addHandler(consoleHandler);
		}

		/* Log to text file. */
		Handler fileHandler = null;
		try {
			if (configManager.getBoolean(EnvironmentParams.JE_LOGGING_FILE)) {

				/* Log with a rotating set of files, use append mode. */
				int limit = configManager
						.getInt(EnvironmentParams.JE_LOGGING_FILE_LIMIT);
				int count = configManager
						.getInt(EnvironmentParams.JE_LOGGING_FILE_COUNT);
				String logFilePattern = envHome + "/" + Tracer.INFO_FILES;

				fileHandler = new FileHandler(logFilePattern, limit, count,
						true);
				fileHandler.setFormatter(new SimpleFormatter());
				fileHandler.setLevel(level);
				logger.addHandler(fileHandler);
			}
		} catch (IOException e) {
			throw new DatabaseException(e.getMessage());
		}

		return logger;
	}

	/**
	 * Add the database log as one of the debug logging destinations when the
	 * logging system is sufficiently initialized.
	 */
	public void enableDebugLoggingToDbLog() throws DatabaseException {

		if (configManager.getBoolean(EnvironmentParams.JE_LOGGING_DBLOG)) {
			Handler dbLogHandler = new TraceLogHandler(this);
			Level level = Level.parse(configManager
					.get(EnvironmentParams.JE_LOGGING_LEVEL));
			dbLogHandler.setLevel(level);
			envLogger.addHandler(dbLogHandler);
		}
	}

	/**
	 * Close down the logger.
	 */
	public void closeLogger() {
		Handler[] handlers = envLogger.getHandlers();
		for (int i = 0; i < handlers.length; i++) {
			handlers[i].close();
		}
	}

	/**
	 * Not much to do, mark state.
	 */
	public void open() {
		envState = DbEnvState.OPEN;
	}

	/**
	 * Invalidate the environment. Done when a fatal exception
	 * (RunRecoveryException) is thrown.
	 */
	public void invalidate(RunRecoveryException e) {

		/*
		 * Remember the fatal exception so we can redisplay it if the
		 * environment is called by the application again. Set some state in the
		 * exception so the exception message will be clear that this was an
		 * earlier exception.
		 */
		savedInvalidatingException = e;
		envState = DbEnvState.INVALID;
		requestShutdownDaemons();
	}

	/**
	 * @return true if environment is open.
	 */
	public boolean isOpen() {
		return (envState == DbEnvState.OPEN);
	}

	/**
	 * @return true if close has begun, although the state may still be open.
	 */
	public boolean isClosing() {
		return closing;
	}

	public boolean isClosed() {
		return (envState == DbEnvState.CLOSED);
	}

	/**
	 * When a RunRecoveryException occurs or the environment is closed, further
	 * writing can cause log corruption.
	 */
	public boolean mayNotWrite() {
		return (envState == DbEnvState.INVALID)
				|| (envState == DbEnvState.CLOSED);
	}

	public void checkIfInvalid() throws RunRecoveryException {

		if (envState == DbEnvState.INVALID) {
			savedInvalidatingException.setAlreadyThrown();
			throw savedInvalidatingException;
		}
	}

	public void checkNotClosed() throws DatabaseException {

		if (envState == DbEnvState.CLOSED) {
			throw new DatabaseException(
					"Attempt to use a Environment that has been closed.");
		}
	}

	public synchronized void close() throws DatabaseException {

		if (--referenceCount <= 0) {
			doClose(true);
		}
	}

	public synchronized void close(boolean doCheckpoint)
			throws DatabaseException {

		if (--referenceCount <= 0) {
			doClose(doCheckpoint);
		}
	}

	private void doClose(boolean doCheckpoint) throws DatabaseException {

		StringBuffer errors = new StringBuffer();

		try {
			Tracer.trace(Level.FINE, this, "Close of environment " + envHome
					+ " started");

			try {
				envState.checkState(DbEnvState.VALID_FOR_CLOSE,
						DbEnvState.CLOSED);
			} catch (DatabaseException DBE) {
				throw DBE;
			}

			/*
			 * Begin shutdown of the deamons before checkpointing. Cleaning
			 * during the checkpoint is wasted and slows down the checkpoint.
			 */
			requestShutdownDaemons();

			/* Checkpoint to bound recovery time. */
			if (doCheckpoint
					&& !isReadOnly
					&& (envState != DbEnvState.INVALID)
					&& logManager.getLastLsnAtRecovery() != fileManager
							.getLastUsedLsn()) {

				/*
				 * Force a checkpoint. Don't allow deltas (minimize recovery
				 * time) because they cause inefficiencies for two reasons: (1)
				 * recovering BINDeltas causes extra random I/O in order to
				 * reconstitute BINS, which can greatly increase recovery time,
				 * and (2) logging deltas during close causes redundant logging
				 * by the full checkpoint after recovery.
				 */
				CheckpointConfig ckptConfig = new CheckpointConfig();
				ckptConfig.setForce(true);
				ckptConfig.setMinimizeRecoveryTime(true);
				try {
					invokeCheckpoint(ckptConfig, false, // flushAll
							"close");
				} catch (DatabaseException IE) {
					errors.append("\nException performing checkpoint: ");
					errors.append(IE.toString()).append("\n");
				}
			}

			try {
				shutdownDaemons();
			} catch (InterruptedException IE) {
				errors.append("\nException shutting down daemon threads: ");
				errors.append(IE.toString()).append("\n");
			}

			/* Flush log. */
			Tracer.trace(Level.FINE, this, "Env " + envHome
					+ " daemons shutdown");
			try {
				logManager.flush();
			} catch (DatabaseException DBE) {
				errors.append("\nException flushing log manager: ");
				errors.append(DBE.toString()).append("\n");
			}

			try {
				fileManager.clear();
			} catch (IOException IOE) {
				errors.append("\nException clearing file manager: ");
				errors.append(IOE.toString()).append("\n");
			} catch (DatabaseException DBE) {
				errors.append("\nException clearing file manager: ");
				errors.append(DBE.toString()).append("\n");
			}

			try {
				fileManager.close();
			} catch (IOException IOE) {
				errors.append("\nException clearing file manager: ");
				errors.append(IOE.toString()).append("\n");
			} catch (DatabaseException DBE) {
				errors.append("\nException clearing file manager: ");
				errors.append(DBE.toString()).append("\n");
			}

			try {
				inMemoryINs.clear();
			} catch (DatabaseException DBE) {
				errors.append("\nException closing file manager: ");
				errors.append(DBE.toString()).append("\n");
			}

			closeLogger();

			DbEnvPool.getInstance().remove(envHome);

			try {
				checkLeaks();
				LatchSupport.clearNotes();
			} catch (DatabaseException DBE) {
				errors.append("\nException performing validity checks: ");
				errors.append(DBE.toString()).append("\n");
			}
		} finally {
			envState = DbEnvState.CLOSED;
		}

		if (errors.length() > 0 && savedInvalidatingException == null) {

			/* Don't whine again if we've already whined. */
			throw new RunRecoveryException(this, errors.toString());
		}
	}

	/*
	 * Clear as many resources as possible, even in the face of an environment
	 * that has received a fatal error, in order to support reopening the
	 * environment in the same JVM.
	 */
	public synchronized void closeAfterRunRecovery() throws DatabaseException {

		try {
			shutdownDaemons();
		} catch (InterruptedException IE) {
			/* Klockwork - ok */
		}

		try {
			fileManager.clear();
		} catch (Exception e) {
			/* Klockwork - ok */
		}

		try {
			fileManager.close();
		} catch (Exception e) {
			/* Klockwork - ok */
		}

		DbEnvPool.getInstance().remove(envHome);
	}

	public synchronized void forceClose() throws DatabaseException {

		referenceCount = 1;
		close();
	}

	public synchronized void incReferenceCount() {
		referenceCount++;
	}

	public static int getThreadLocalReferenceCount() {
		return threadLocalReferenceCount;
	}

	public static synchronized void incThreadLocalReferenceCount() {
		threadLocalReferenceCount++;
	}

	public static synchronized void decThreadLocalReferenceCount() {
		threadLocalReferenceCount--;
	}

	public static boolean getNoComparators() {
		return noComparators;
	}

	/**
	 * Debugging support. Check for leaked locks and transactions.
	 */
	private void checkLeaks() throws DatabaseException {

		/* Only enabled if this check leak flag is true. */
		if (!configManager.getBoolean(EnvironmentParams.ENV_CHECK_LEAKS)) {
			return;
		}

		boolean clean = true;
		StatsConfig statsConfig = new StatsConfig();

		/* Fast stats will not return NTotalLocks below. */
		statsConfig.setFast(false);

		LockStats lockStat = lockStat(statsConfig);
		if (lockStat.getNTotalLocks() != 0) {
			clean = false;
			System.out.println("Problem: " + lockStat.getNTotalLocks()
					+ " locks left");
			txnManager.getLockManager().dump();
		}

		TransactionStats txnStat = txnStat(statsConfig);
		if (txnStat.getNActive() != 0) {
			clean = false;
			System.out.println("Problem: " + txnStat.getNActive()
					+ " txns left");
			TransactionStats.Active[] active = txnStat.getActiveTxns();
			if (active != null) {
				for (int i = 0; i < active.length; i += 1) {
					System.out.println(active[i]);
				}
			}
		}

		if (LatchSupport.countLatchesHeld() > 0) {
			clean = false;
			System.out.println("Some latches held at env close.");
			LatchSupport.dumpLatchesHeld();
		}

		assert clean : "Lock, transaction, or latch left behind at environment close";
	}

	/**
	 * Invoke a checkpoint programatically. Note that only one checkpoint may
	 * run at a time.
	 */
	public boolean invokeCheckpoint(CheckpointConfig config, boolean flushAll,
			String invokingSource) throws DatabaseException {

		if (checkpointer != null) {
			checkpointer.doCheckpoint(config, flushAll, invokingSource);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Flip the log to a new file, forcing an fsync. Return the LSN of the trace
	 * record in the new file.
	 */
	public long forceLogFileFlip() throws DatabaseException {

		Tracer newRec = new Tracer("File Flip");
		return logManager.logForceFlip(newRec);
	}

	/**
	 * Invoke a compress programatically. Note that only one compress may run at
	 * a time.
	 */
	public boolean invokeCompressor() throws DatabaseException {

		if (inCompressor != null) {
			inCompressor.doCompress();
			return true;
		} else {
			return false;
		}
	}

	public void invokeEvictor() throws DatabaseException {

		if (evictor != null) {
			evictor.doEvict(Evictor.SOURCE_MANUAL);
		}
	}

	public int invokeCleaner() throws DatabaseException {

		if (cleaner != null) {
			return cleaner.doClean(true, // cleanMultipleFiles
					false); // forceCleaning
		} else {
			return 0;
		}
	}

	private void requestShutdownDaemons() {

		closing = true;

		if (inCompressor != null) {
			inCompressor.requestShutdown();
		}

		if (evictor != null) {
			evictor.requestShutdown();
		}

		if (checkpointer != null) {
			checkpointer.requestShutdown();
		}

		if (cleaner != null) {
			cleaner.requestShutdown();
		}
	}

	/**
	 * Ask all daemon threads to shut down.
	 */
	private void shutdownDaemons() throws InterruptedException {

		shutdownINCompressor();

		/*
		 * Cleaner has to be shutdown before checkpointer because former calls
		 * the latter.
		 */
		shutdownCleaner();
		shutdownCheckpointer();

		/*
		 * The evictor has to get shutdown last because the other daemons might
		 * create changes to the memory usage which result in a notify to
		 * eviction.
		 */
		shutdownEvictor();
	}

	/**
	 * Available for the unit tests.
	 */
	public void shutdownINCompressor() throws InterruptedException {

		if (inCompressor != null) {
			inCompressor.shutdown();

			/*
			 * If daemon thread doesn't shutdown for any reason, at least clear
			 * the reference to the environment so it can be GC'd.
			 */
			inCompressor.clearEnv();
			inCompressor = null;
		}
		return;
	}

	public void shutdownEvictor() throws InterruptedException {

		if (evictor != null) {
			evictor.shutdown();

			/*
			 * If daemon thread doesn't shutdown for any reason, at least clear
			 * the reference to the environment so it can be GC'd.
			 */
			evictor.clearEnv();
			evictor = null;
		}
		return;
	}

	void shutdownCheckpointer() throws InterruptedException {

		if (checkpointer != null) {
			checkpointer.shutdown();

			/*
			 * If daemon thread doesn't shutdown for any reason, at least clear
			 * the reference to the environment so it can be GC'd.
			 */
			checkpointer.clearEnv();
			checkpointer = null;
		}
		return;
	}

	/**
	 * public for unit tests.
	 */
	public void shutdownCleaner() throws InterruptedException {

		if (cleaner != null) {
			cleaner.shutdown();

			/*
			 * Don't call clearEnv -- Cleaner.shutdown does this for each
			 * cleaner thread. Don't set the cleaner field to null because we
			 * use it to get the utilization profile and tracker.
			 */
		}
		return;
	}

	public boolean isNoLocking() {
		return isNoLocking;
	}

	public boolean isTransactional() {
		return isTransactional;
	}

	public boolean isReadOnly() {
		return isReadOnly;
	}

	public static boolean getFairLatches() {
		return fairLatches;
	}



	/* DatabaseImpl access. */
	public DatabaseImpl createDb(Locker locker, String databaseName,
			DatabaseConfig dbConfig, Database databaseHandle)
			throws DatabaseException {

		return dbMapTree.createDb(locker, databaseName, dbConfig,
				databaseHandle);
	}

	/**
	 * Get a database object given a database name.
	 * 
	 * @param databaseName
	 *            target database.
	 * 
	 * @return null if database doesn't exist.
	 */
	public DatabaseImpl getDb(Locker locker, String databaseName,
			Database databaseHandle) throws DatabaseException {

		return dbMapTree.getDb(locker, databaseName, databaseHandle);
	}

	public List getDbNames() throws DatabaseException {

		return dbMapTree.getDbNames();
	}

	/**
	 * For debugging.
	 */
	public void dumpMapTree() throws DatabaseException {

		dbMapTree.dump();
	}

	/**
	 * Rename a database.
	 */
	public void dbRename(Locker locker, String databaseName, String newName)
			throws DatabaseException {

		dbMapTree.dbRename(locker, databaseName, newName);
	}

	/**
	 * Remove a database.
	 */
	public void dbRemove(Locker locker, String databaseName)
			throws DatabaseException {

		dbMapTree.dbRemove(locker, databaseName);
	}

	/**
	 * Truncate a database. Return a new DatabaseImpl object which represents
	 * the new truncated database. The old database is marked as deleted.
	 * 
	 * @deprecated This supports Database.truncate(), which is deprecated.
	 */
	public TruncateResult truncate(Locker locker, DatabaseImpl database)
			throws DatabaseException {

		return dbMapTree.truncate(locker, database, true);
	}

	/**
	 * Truncate a database.
	 */
	public long truncate(Locker locker, String databaseName, boolean returnCount)
			throws DatabaseException {

		return dbMapTree.truncate(locker, databaseName, returnCount);
	}

	/**
	 * Transactional services.
	 */
	public Txn txnBegin(Transaction parent, TransactionConfig txnConfig)
			throws DatabaseException {

		if (!isTransactional) {
			throw new DatabaseException("beginTransaction called, "
					+ " but Environment was not opened "
					+ "with transactional cpabilities");
		}

		return txnManager.txnBegin(parent, txnConfig);
	}

	/* Services. */
	public LogManager getLogManager() {
		return logManager;
	}

	public FileManager getFileManager() {
		return fileManager;
	}

	public DbTree getDbMapTree() {
		return dbMapTree;
	}

	/**
	 * Returns the config manager for the current base configuration.
	 * 
	 * <p>
	 * The configuration can change, but changes are made by replacing the
	 * config manager object with a enw one. To use a consistent set of
	 * properties, call this method once and query the returned manager
	 * repeatedly for each property, rather than getting the config manager via
	 * this method for each property individually.
	 * </p>
	 */
	public DbConfigManager getConfigManager() {
		return configManager;
	}

	/**
	 * Clones the current configuration.
	 */
	public EnvironmentConfig cloneConfig() {
		return DbInternal.cloneConfig(configManager.getEnvironmentConfig());
	}

	/**
	 * Clones the current mutable configuration.
	 */
	public EnvironmentMutableConfig cloneMutableConfig() {
		return DbInternal.cloneMutableConfig(configManager
				.getEnvironmentConfig());
	}

	/**
	 * Throws an exception if an immutable property is changed.
	 */
	public void checkImmutablePropsForEquality(EnvironmentConfig config)
			throws IllegalArgumentException {

		DbInternal.checkImmutablePropsForEquality(configManager
				.getEnvironmentConfig(), config);
	}

	/**
	 * Changes the mutable config properties that are present in the given
	 * config, and notifies all config observer.
	 */
	public synchronized void setMutableConfig(EnvironmentMutableConfig config)
			throws DatabaseException {

		/* Clone the current config. */
		EnvironmentConfig newConfig = DbInternal.cloneConfig(configManager
				.getEnvironmentConfig());

		/* Copy in the mutable props. */
		DbInternal.copyMutablePropsTo(config, newConfig);

		/*
		 * Update the current config and notify observers. The config manager is
		 * replaced with a new instance that uses the new configuration. This
		 * avoid synchronization issues: other threads that have a referenced to
		 * the old configuration object are not impacted.
		 * 
		 * Notify listeners in reverse order of registration so that the
		 * environment listener is notified last and it can start daemon threads
		 * after they are configured.
		 */
		configManager = new DbConfigManager(newConfig);
		for (int i = configObservers.size() - 1; i >= 0; i -= 1) {
			EnvConfigObserver o = (EnvConfigObserver) configObservers.get(i);
			o.envConfigUpdate(configManager);
		}
	}

	/**
	 * Adds an observer of mutable config changes.
	 */
	public synchronized void addConfigObserver(EnvConfigObserver o) {
		configObservers.add(o);
	}

	/**
	 * Removes an observer of mutable config changes.
	 */
	public synchronized void removeConfigObserver(EnvConfigObserver o) {
		configObservers.remove(o);
	}

	public INList getInMemoryINs() {
		return inMemoryINs;
	}

	public TxnManager getTxnManager() {
		return txnManager;
	}

	public Checkpointer getCheckpointer() {
		return checkpointer;
	}

	public Cleaner getCleaner() {
		return cleaner;
	}

	public MemoryBudget getMemoryBudget() {
		return memoryBudget;
	}

	/**
	 * @return environment Logger, for use in debugging output.
	 */
	public Logger getLogger() {
		return envLogger;
	}

	/*
	 * Verification, must be run while system is quiescent.
	 */
	public boolean verify(VerifyConfig config, PrintStream out)
			throws DatabaseException {

		/* For now, verify all databases */
		return dbMapTree.verify(config, out);
	}

	public void verifyCursors() throws DatabaseException {

		inCompressor.verifyCursors();
	}

	/*
	 * Statistics
	 */

	/**
	 * Retrieve and return stat information.
	 */
	synchronized public EnvironmentStats loadStats(StatsConfig config)
			throws DatabaseException {

		EnvironmentStats stats = new EnvironmentStats();
		inCompressor.loadStats(config, stats);
		evictor.loadStats(config, stats);
		checkpointer.loadStats(config, stats);
		cleaner.loadStats(config, stats);
		logManager.loadStats(config, stats);
		memoryBudget.loadStats(config, stats);
		return stats;
	}

	/**
	 * Retrieve lock statistics
	 */
	synchronized public LockStats lockStat(StatsConfig config)
			throws DatabaseException {

		return txnManager.lockStat(config);
	}

	/**
	 * Retrieve txn statistics
	 */
	synchronized public TransactionStats txnStat(StatsConfig config)
			throws DatabaseException {

		return txnManager.txnStat(config);
	}

	public int getINCompressorQueueSize() throws DatabaseException {

		return inCompressor.getBinRefQueueSize();
	}

	/**
	 * Info about the last recovery
	 */
	public RecoveryInfo getLastRecoveryInfo() {
		return lastRecoveryInfo;
	}

	/**
	 * Get the environment home directory.
	 */
	public File getEnvironmentHome() {
		return envHome;
	}

	public long getTxnTimeout() {
		return txnTimeout;
	}

	public long getLockTimeout() {
		return lockTimeout;
	}

	/**
	 * Returns the shared trigger latch.
	 */
	public SharedLatch getTriggerLatch() {
		return triggerLatch;
	}

	public Evictor getEvictor() {
		return evictor;
	}

	void alertEvictor() {
		if (evictor != null) {
			evictor.alert();
		}
	}

	/**
	 * For stress testing. Should only ever be called from an assert.
	 */
	public static boolean maybeForceYield() {
		if (forcedYield) {
			Thread.yield();
		}
		return true; // so assert doesn't fire
	}
}
