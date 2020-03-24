/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Sleepycat Software.  All rights reserved.
 *
 * $Id: Environment.java,v 1.1 2006/05/06 08:59:35 ckaestne Exp $
 */

package com.sleepycat.je;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;

import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.DbEnvPool;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.txn.LockerFactory;
import com.sleepycat.je.utilint.Tracer;

/**
 * Javadoc for this public class is generated via the doc templates in the
 * doc_src directory.
 */
public class Environment {

	/*
	 * The name of the je properties file, to be found in the environment
	 * directory.
	 */
	private static final String PROPFILE_NAME = "je.properties";

	protected EnvironmentImpl environmentImpl;

	private TransactionConfig defaultTxnConfig;

	private EnvironmentMutableConfig handleConfig;

	private Set referringDbs;

	private Set referringDbTxns;

	private boolean valid;

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public Environment(File envHome, EnvironmentConfig configuration)
			throws DatabaseException {

		environmentImpl = null;
		referringDbs = Collections.synchronizedSet(new HashSet());
		referringDbTxns = Collections.synchronizedSet(new HashSet());
		valid = false;

		DatabaseUtil.checkForNullParam(envHome, "envHome");

		/* If the user specified a null object, use the default */
		EnvironmentConfig baseConfig = (configuration == null) ? EnvironmentConfig.DEFAULT
				: configuration;

		/* Make a copy, apply je.properties, and init the handle config. */
		EnvironmentConfig useConfig = baseConfig.cloneConfig();
		applyFileConfig(envHome, useConfig);
		copyToHandleConfig(useConfig, useConfig);

		/* Look in the shared pool for this environment. */
		DbEnvPool.EnvironmentImplInfo envInfo = DbEnvPool.getInstance()
				.getEnvironment(envHome, useConfig);
		environmentImpl = envInfo.envImpl;

		/* Check if the environmentImpl is valid. */
		environmentImpl.checkIfInvalid();

		if (!envInfo.firstHandle && configuration != null) {

			/* Perform all environment config updates atomically. */
			synchronized (environmentImpl) {

				/*
				 * If a non-null configuration parameter was passed in and this
				 * is not the handle that created the underlying
				 * EnvironmentImpl, check that the configuration parameters
				 * specified for this open match those of the currently open
				 * environment. An exception is thrown if the check fails.
				 * 
				 * Don't do this check if this handle created the environment
				 * because the creation might have modified the parameters,
				 * which would create a Catch-22 in terms of validation. For
				 * example, je.maxMemory will be overridden if the JVM's -mx
				 * flag is less than that setting, so the initial handle's
				 * config object won't be the same as the passed in config.
				 */
				environmentImpl.checkImmutablePropsForEquality(useConfig);
			}
		}

		if (!valid) {
			valid = true;
		}

		/* Successful, increment reference count */
		environmentImpl.incReferenceCount();
	}

	/**
	 * Get an Environment for an existing EnvironmentImpl. Used by utilities
	 * such as the JMX MBean which don't want to open the environment or be
	 * reference counted. The calling application must take care not to retain
	 * the the doc templates in the doc_src directory.
	 */
	Environment(File envHome) throws DatabaseException {

		environmentImpl = null;
		valid = false;

		/* Look in the shared pool for this environment. */
		DbEnvPool.EnvironmentImplInfo envInfo = DbEnvPool.getInstance()
				.getExistingEnvironment(envHome);

		EnvironmentImpl foundImpl = envInfo.envImpl;
		if (foundImpl != null) {
			/* Check if the environmentImpl is valid. */
			foundImpl.checkIfInvalid();

			/* Successful, increment reference count */
			environmentImpl = foundImpl;
			environmentImpl.incReferenceCount();

			/*
			 * Initialize the handle's environment config, so that it's valid to
			 * call setConfig and getConfig against this handle. Make a copy,
			 * apply je.properties, and init the handle config.
			 */
			EnvironmentConfig useConfig = EnvironmentConfig.DEFAULT
					.cloneConfig();
			applyFileConfig(envHome, useConfig);
			copyToHandleConfig(useConfig, useConfig);

			/* Need this in order to open database handles. */
			referringDbs = Collections.synchronizedSet(new HashSet());

			valid = true;
		}
	}

	/**
	 * Apply the configurations specified in the je.properties file to override
	 * any programatically set configurations.
	 */
	private void applyFileConfig(File envHome,
			EnvironmentMutableConfig useConfig) throws IllegalArgumentException {

		/* Apply the je.properties file. */
		if (useConfig.getLoadPropertyFile()) {
			File paramFile = null;
			try {
				paramFile = new File(envHome, PROPFILE_NAME);
				Properties fileProps = new Properties();
				FileInputStream fis = new FileInputStream(paramFile);
				fileProps.load(fis);
				fis.close();

				/* Validate the existing file. */
				useConfig.validateProperties(fileProps);

				/* Add them to the configuration object. */
				Iterator iter = fileProps.entrySet().iterator();
				while (iter.hasNext()) {
					Map.Entry propPair = (Map.Entry) iter.next();
					String name = (String) propPair.getKey();
					String value = (String) propPair.getValue();
					useConfig.setConfigParam(name, value);
				}
			} catch (FileNotFoundException e) {

				/*
				 * Klockwork - ok Eat the exception, okay if the file doesn't
				 * exist.
				 */
			} catch (IOException e) {
				IllegalArgumentException e2 = new IllegalArgumentException(
						"An error occurred when reading " + paramFile);
				e2.initCause(e);
				throw e2;
			}
		}
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public synchronized void close() throws DatabaseException {

		checkHandleIsValid();
		try {
			checkEnv();
		} catch (RunRecoveryException e) {

			/*
			 * We're trying to close on an environment that has seen a fatal
			 * exception. Try to do the minimum, such as closing file
			 * descriptors, to support re-opening the environment in the same
			 * jvm.
			 */
			if (environmentImpl != null) {
				environmentImpl.closeAfterRunRecovery();
			}
			return;
		}

		StringBuffer errors = new StringBuffer();
		try {
			if (referringDbs != null) {
				int nDbs = referringDbs.size();
				if (nDbs != 0) {
					errors.append("There ");
					if (nDbs == 1) {
						errors
								.append("is 1 open Database in the Environment.\n");
					} else {
						errors.append("are ");
						errors.append(nDbs);
						errors.append(" open Database in the Environment.\n");
					}
					errors.append("Closing the following databases:\n");

					Iterator iter = referringDbs.iterator();
					while (iter.hasNext()) {
						Database db = (Database) iter.next();
						/*
						 * Save the db name before we attempt the close, it's
						 * unavailable after the close.
						 */
						String dbName = db.getDebugName();
						errors.append(dbName).append(" ");
						try {
							db.close();
						} catch (RunRecoveryException e) {
							throw e;
						} catch (DatabaseException DBE) {
							errors.append("\nWhile closing Database ");
							errors.append(dbName);
							errors.append(" encountered exception: ");
							errors.append(DBE).append("\n");
						}
					}
				}
			}

			if (referringDbTxns != null) {
				int nTxns = referringDbTxns.size();
				if (nTxns != 0) {
					Iterator iter = referringDbTxns.iterator();
					errors.append("There ");
					if (nTxns == 1) {
						errors
								.append("is 1 existing transaction opened against");
						errors.append(" the Environment.\n");
					} else {
						errors.append("are ");
						errors.append(nTxns);
						errors.append(" existing transactions opened against");
						errors.append(" the Environment.\n");
					}
					errors.append("Aborting open transactions ...\n");

					while (iter.hasNext()) {
						Transaction txn = (Transaction) iter.next();
						try {
							txn.abort();
						} catch (RunRecoveryException e) {
							throw e;
						} catch (DatabaseException DBE) {
							errors.append("\nWhile aborting transaction ");
							errors.append(txn.getId());
							errors.append(" encountered exception: ");
							errors.append(DBE).append("\n");
						}
					}
				}
			}

			try {
				environmentImpl.close();
			} catch (RunRecoveryException e) {
				throw e;
			} catch (DatabaseException DBE) {
				errors
						.append("\nWhile closing Environment encountered exception: ");
				errors.append(DBE).append("\n");
			}
		} finally {
			environmentImpl = null;
			valid = false;
			if (errors.length() > 0) {
				throw new DatabaseException(errors.toString());
			}
		}
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public synchronized Database openDatabase(Transaction txn,
			String databaseName, DatabaseConfig dbConfig)
			throws DatabaseException {

		if (dbConfig == null) {
			dbConfig = DatabaseConfig.DEFAULT;
		}
		Database db = new Database(this);
		openDb(txn, db, databaseName, dbConfig, false);
		return db;
	}

	/**
	 * Javadoc for this public class is generated via the doc templates in the
	 * doc_src directory.
	 */
	public synchronized SecondaryDatabase openSecondaryDatabase(
			Transaction txn, String databaseName, Database primaryDatabase,
			SecondaryConfig dbConfig) throws DatabaseException {

		if (dbConfig == null) {
			dbConfig = SecondaryConfig.DEFAULT;
		}
		SecondaryDatabase db = new SecondaryDatabase(this, dbConfig,
				primaryDatabase);
		openDb(txn, db, databaseName, dbConfig, dbConfig.getAllowPopulate());
		return db;
	}

	private void openDb(Transaction txn, Database newDb, String databaseName,
			DatabaseConfig dbConfig, boolean needWritableLockerForInit)
			throws DatabaseException {

		checkEnv();
		DatabaseUtil.checkForNullParam(databaseName, "databaseName");

		Tracer.trace(Level.FINEST, environmentImpl, "Environment.open: "
				+ " name=" + databaseName + " dbConfig=" + dbConfig);

		/*
		 * Check that the open configuration doesn't conflict with the
		 * environmentImpl configuration.
		 */
		validateDbConfigAgainstEnv(dbConfig, databaseName);

		Locker locker = null;
		boolean operationOk = false;
		boolean dbIsClosing = false;
		try {

			/*
			 * Does this database exist? Get a transaction to use. If the
			 * database exists already, we really only need a readable locker.
			 * If the database must be created, we need a writable one.
			 * Unfortunately, we have to get the readable one first before we
			 * know whether we have to create. However, if we need to write
			 * during initialization (to populate a secondary for example), then
			 * just create a writable locker now.
			 */
			boolean isWritableLocker = true;
			if (needWritableLockerForInit) {
				locker = LockerFactory.getWritableLocker(this, txn, dbConfig
						.getTransactional(), true, // retainNonTxnLocks
						null);
				isWritableLocker = true;
			} else {
				locker = LockerFactory.getReadableLocker(this, txn, dbConfig
						.getTransactional(), true, // retainNonTxnLocks
						false); // readCommittedIsolation
				isWritableLocker = !dbConfig.getTransactional()
						|| locker.isTransactional();
			}

			DatabaseImpl database = environmentImpl.getDb(locker, databaseName,
					newDb);
			boolean databaseExists = (database == null) ? false : ((database
					.isDeleted()) ? false : true);// TODO conditinal
													// expression similar to
													// ifstatement

			if (databaseExists) {
				if (dbConfig.getAllowCreate() && dbConfig.getExclusiveCreate()) {
					/* We intended to create this, but it already exists. */
					dbIsClosing = true;
					throw new DatabaseException("Database " + databaseName
							+ " already exists");
				}

				newDb.initExisting(this, locker, database, dbConfig);
			} else {
				/* No database. Create if we're allowed to. */
				if (dbConfig.getAllowCreate()) {

					/*
					 * We're going to have to do some writing. Switch to a
					 * writable locker if we don't already have one.
					 */
					if (!isWritableLocker) {
						locker.operationEnd(OperationStatus.SUCCESS);
						locker = LockerFactory.getWritableLocker(this, txn,
								dbConfig.getTransactional(), true, // retainNonTxnLocks
								null);
						isWritableLocker = true;
					}

					newDb.initNew(this, locker, databaseName, dbConfig);
				} else {
					/* We aren't allowed to create this database. */
					throw new DatabaseNotFoundException("Database "
							+ databaseName + " not found.");
				}
			}

			operationOk = true;
			addReferringHandle(newDb);
		} finally {

			/*
			 * Tell the transaction that this operation is over. Some types of
			 * transactions (BasicLocker and AutoTxn) will actually finish. The
			 * transaction can decide if it is finishing and if it needs to
			 * transfer the db handle lock it owns to someone else.
			 */
			if (locker != null) {
				locker.setHandleLockOwner(operationOk, newDb, dbIsClosing);
				locker.operationEnd(operationOk);
			}
		}
	}

	private void validateDbConfigAgainstEnv(DatabaseConfig dbConfig,
			String databaseName) throws DatabaseException {

		/* Check operation's transactional status against the Environment */
		if (dbConfig.getTransactional() && !(environmentImpl.isTransactional())) {
			throw new DatabaseException("Attempted to open Database "
					+ databaseName
					+ " transactionally, but parent Environment is"
					+ " not transactional");
		}

		/* Check read/write status */
		if (environmentImpl.isReadOnly() && (!dbConfig.getReadOnly())) {
			throw new DatabaseException("Attempted to open Database "
					+ databaseName
					+ " as writable but parent Environment is read only ");
		}
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public void removeDatabase(Transaction txn, String databaseName)
			throws DatabaseException {

		checkHandleIsValid();
		checkEnv();
		DatabaseUtil.checkForNullParam(databaseName, "databaseName");

		Locker locker = null;
		boolean operationOk = false;
		try {

			/*
			 * Note: use env level isTransactional as proxy for the db
			 * isTransactional.
			 */
			locker = LockerFactory.getWritableLocker(this, txn, environmentImpl
					.isTransactional(), true /* retainNonTxnLocks */, null);
			environmentImpl.dbRemove(locker, databaseName);
			operationOk = true;
		} finally {
			if (locker != null) {
				locker.operationEnd(operationOk);
			}
		}
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public void renameDatabase(Transaction txn, String databaseName,
			String newName) throws DatabaseException {

		DatabaseUtil.checkForNullParam(databaseName, "databaseName");
		DatabaseUtil.checkForNullParam(newName, "newName");

		checkHandleIsValid();
		checkEnv();

		Locker locker = null;
		boolean operationOk = false;
		try {

			/*
			 * Note: use env level isTransactional as proxy for the db
			 * isTransactional.
			 */
			locker = LockerFactory.getWritableLocker(this, txn, environmentImpl
					.isTransactional(), true /* retainNonTxnLocks */, null);
			environmentImpl.dbRename(locker, databaseName, newName);
			operationOk = true;
		} finally {
			if (locker != null) {
				locker.operationEnd(operationOk);
			}
		}
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public long truncateDatabase(Transaction txn, String databaseName,
			boolean returnCount) throws DatabaseException {

		checkHandleIsValid();
		checkEnv();
		DatabaseUtil.checkForNullParam(databaseName, "databaseName");

		Locker locker = null;
		boolean operationOk = false;
		long count = 0;
		try {

			/*
			 * Note: use env level isTransactional as proxy for the db
			 * isTransactional.
			 */
			locker = LockerFactory.getWritableLocker(this, txn, environmentImpl
					.isTransactional(), true /* retainNonTxnLocks */, null);

			count = environmentImpl.truncate(locker, databaseName, returnCount);

			operationOk = true;
		} finally {
			if (locker != null) {
				locker.operationEnd(operationOk);
			}
		}
		return count;
	}

	/**
	 * Returns the current memory usage in bytes for all btrees in the
	 * environmentImpl.
	 */
	long getMemoryUsage() throws DatabaseException {

		checkHandleIsValid();
		checkEnv();

		return environmentImpl.getMemoryBudget().getCacheMemoryUsage();
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public File getHome() throws DatabaseException {

		checkHandleIsValid();
		return environmentImpl.getEnvironmentHome();
	}

	/*
	 * Transaction management
	 */

	/**
	 * Returns the default txn config for this environment handle.
	 */
	TransactionConfig getDefaultTxnConfig() {
		return defaultTxnConfig;
	}

	/**
	 * Copies the handle properties out of the config properties, and
	 * initializes the default transaction config.
	 */
	private void copyToHandleConfig(EnvironmentMutableConfig useConfig,
			EnvironmentConfig initStaticConfig) throws DatabaseException {

		/*
		 * Create the new objects, initialize them, then change the instance
		 * fields. This avoids synchronization issues.
		 */
		EnvironmentMutableConfig newHandleConfig = new EnvironmentMutableConfig();
		useConfig.copyHandlePropsTo(newHandleConfig);
		this.handleConfig = newHandleConfig;

		TransactionConfig newTxnConfig = TransactionConfig.DEFAULT
				.cloneConfig();
		newTxnConfig.setNoSync(handleConfig.getTxnNoSync());
		newTxnConfig.setWriteNoSync(handleConfig.getTxnWriteNoSync());
		if (initStaticConfig != null) {
			newTxnConfig.setSerializableIsolation(initStaticConfig
					.getTxnSerializableIsolation());
			newTxnConfig.setReadCommitted(initStaticConfig
					.getTxnReadCommitted());
		} else {
			newTxnConfig.setSerializableIsolation(defaultTxnConfig
					.getSerializableIsolation());
			newTxnConfig.setReadCommitted(defaultTxnConfig.getReadCommitted());
		}
		this.defaultTxnConfig = newTxnConfig;
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public Transaction beginTransaction(Transaction parent,
			TransactionConfig txnConfig) throws DatabaseException {

		checkHandleIsValid();
		checkEnv();

		if (!environmentImpl.isTransactional()) {
			throw new DatabaseException(
					"Transactions can not be used in a non-transactional "
							+ "environment");
		}

		if (txnConfig != null
				&& ((txnConfig.getSerializableIsolation() && txnConfig
						.getReadUncommitted())
						|| (txnConfig.getSerializableIsolation() && txnConfig
								.getReadCommitted()) || (txnConfig
						.getReadUncommitted() && txnConfig.getReadCommitted()))) {
			throw new IllegalArgumentException(
					"Only one may be specified: SerializableIsolation, "
							+ "ReadCommitted or ReadUncommitted");
		}

		/*
		 * Apply txn config defaults. We don't need to clone unless we have to
		 * apply the env default, since we don't hold onto a txn config
		 * reference.
		 */
		TransactionConfig useConfig = null;
		if (txnConfig == null) {
			useConfig = defaultTxnConfig;
		} else {
			if (defaultTxnConfig.getNoSync()
					|| defaultTxnConfig.getWriteNoSync()) {

				/*
				 * The environment sync settings have been set, check if any
				 * were set in the user's txn config. If none were set in the
				 * user's config, apply the environment defaults
				 */
				if (!txnConfig.getNoSync() && !txnConfig.getSync()
						&& !txnConfig.getWriteNoSync()) {
					useConfig = txnConfig.cloneConfig();
					if (defaultTxnConfig.getWriteNoSync()) {
						useConfig.setWriteNoSync(true);
					} else {
						useConfig.setNoSync(true);
					}
				}
			}

			/* Apply isolation level default. */
			if (!txnConfig.getSerializableIsolation()
					&& !txnConfig.getReadCommitted()
					&& !txnConfig.getReadUncommitted()) {
				if (defaultTxnConfig.getSerializableIsolation()) {
					if (useConfig == null) {
						useConfig = txnConfig.cloneConfig();
					}
					useConfig.setSerializableIsolation(true);
				} else if (defaultTxnConfig.getReadCommitted()) {
					if (useConfig == null) {
						useConfig = txnConfig.cloneConfig();
					}
					useConfig.setReadCommitted(true);
				}
			}

			/* No environment level defaults applied. */
			if (useConfig == null) {
				useConfig = txnConfig;
			}
		}

		Transaction txn = new Transaction(this, environmentImpl.txnBegin(
				parent, useConfig));
		addReferringHandle(txn);
		return txn;
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public void checkpoint(CheckpointConfig ckptConfig)
			throws DatabaseException {

		checkHandleIsValid();
		checkEnv();
		CheckpointConfig useConfig = (ckptConfig == null) ? CheckpointConfig.DEFAULT
				: ckptConfig;

		environmentImpl.invokeCheckpoint(useConfig, false, // flushAll
				"api");
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public void sync() throws DatabaseException {

		checkHandleIsValid();
		checkEnv();
		CheckpointConfig config = new CheckpointConfig();
		config.setForce(true);
		environmentImpl.invokeCheckpoint(config, true, // flushAll
				"sync");
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public int cleanLog() throws DatabaseException {

		checkHandleIsValid();
		checkEnv();
		return environmentImpl.invokeCleaner();
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public void evictMemory() throws DatabaseException {

		checkHandleIsValid();
		checkEnv();
		environmentImpl.invokeEvictor();
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public void compress() throws DatabaseException {

		checkHandleIsValid();
		checkEnv();
		environmentImpl.invokeCompressor();
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public EnvironmentConfig getConfig() throws DatabaseException {

		checkHandleIsValid();
		EnvironmentConfig config = environmentImpl.cloneConfig();
		handleConfig.copyHandlePropsTo(config);
		config.fillInEnvironmentGeneratedProps(environmentImpl);
		return config;
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public void setMutableConfig(EnvironmentMutableConfig mutableConfig)
			throws DatabaseException {

		checkHandleIsValid();
		DatabaseUtil.checkForNullParam(mutableConfig, "mutableConfig");

		/*
		 * Change the mutable properties specified in the given configuratation.
		 */
		environmentImpl.setMutableConfig(mutableConfig);

		/* Reset the handle config properties. */
		copyToHandleConfig(mutableConfig, null);
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public EnvironmentMutableConfig getMutableConfig() throws DatabaseException {

		checkHandleIsValid();
		EnvironmentMutableConfig config = environmentImpl.cloneMutableConfig();
		handleConfig.copyHandlePropsTo(config);
		return config;
	}

	/**
	 * Not public yet, since there's nothing to upgrade.
	 */
	void upgrade() throws DatabaseException {

		/* Do nothing. Nothing to upgrade yet. */
	}

	/**
	 * General stats
	 */

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public EnvironmentStats getStats(StatsConfig config)
			throws DatabaseException {

		StatsConfig useConfig = (config == null) ? StatsConfig.DEFAULT : config;

		if (environmentImpl != null) {
			return environmentImpl.loadStats(useConfig);
		} else {
			return new EnvironmentStats();
		}
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public LockStats getLockStats(StatsConfig config) throws DatabaseException {

		checkHandleIsValid();
		checkEnv();
		StatsConfig useConfig = (config == null) ? StatsConfig.DEFAULT : config;

		return environmentImpl.lockStat(useConfig);
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public TransactionStats getTransactionStats(StatsConfig config)
			throws DatabaseException {

		checkHandleIsValid();
		checkEnv();
		StatsConfig useConfig = (config == null) ? StatsConfig.DEFAULT : config;
		return environmentImpl.txnStat(useConfig);
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public List getDatabaseNames() throws DatabaseException {

		checkHandleIsValid();
		checkEnv();
		return environmentImpl.getDbNames();
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public boolean verify(VerifyConfig config, PrintStream out)
			throws DatabaseException {

		checkHandleIsValid();
		checkEnv();
		VerifyConfig useConfig = (config == null) ? VerifyConfig.DEFAULT
				: config;
		return environmentImpl.verify(useConfig, out);
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public Transaction getThreadTransaction() throws DatabaseException {

		return (Transaction) environmentImpl.getTxnManager().getTxnForThread();
	}

	/**
	 * Javadoc for this public method is generated via the doc templates in the
	 * doc_src directory.
	 */
	public void setThreadTransaction(Transaction txn) {

		environmentImpl.getTxnManager().setTxnForThread(txn);
	}

	/*
	 * Non public api -- helpers
	 */

	/*
	 * Let the Environment remember what's opened against it.
	 */
	void addReferringHandle(Database db) {
		referringDbs.add(db);
	}

	/**
	 * Let the Environment remember what's opened against it.
	 */
	void addReferringHandle(Transaction txn) {
		referringDbTxns.add(txn);
	}

	/**
	 * The referring db has been closed.
	 */
	void removeReferringHandle(Database db) {
		referringDbs.remove(db);
	}

	/**
	 * The referring Transaction has been closed.
	 */
	void removeReferringHandle(Transaction txn) {
		referringDbTxns.remove(txn);
	}

	/*
	 * Debugging aids.
	 */

	/**
	 * Internal entrypoint.
	 */
	EnvironmentImpl getEnvironmentImpl() {
		return environmentImpl;
	}

	protected void checkHandleIsValid() throws DatabaseException {

		if (!valid) {
			throw new DatabaseException(
					"Attempt to use non-open Environment object().");
		}
	}

	/**
	 * Throws if the environmentImpl is invalid.
	 */
	protected void checkEnv() throws DatabaseException, RunRecoveryException {

		if (environmentImpl == null) {
			return;
		}
		environmentImpl.checkIfInvalid();
		environmentImpl.checkNotClosed();
	}
}
