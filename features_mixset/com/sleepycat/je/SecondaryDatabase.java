/*PLEASE DO NOT EDIT THIS CODE*/
/*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/

package com.sleepycat.je;
import de.ovgu.cide.jakutil.*;
import com.sleepycat.je.txn.LockerFactory;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.dbi.CursorImpl.SearchMode;
import com.sleepycat.je.dbi.PutMode;
import com.sleepycat.je.dbi.GetMode;
import com.sleepycat.je.dbi.DatabaseImpl;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Set;
import java.util.Iterator;
import java.util.HashSet;
import java.util.Collections;

// line 3 "../../../SecondaryDatabase.ump"
// line 3 "../../../LoggingFinest_SecondaryDatabase.ump"
// line 3 "../../../LoggingFinest_SecondaryDatabase_inner.ump"
// line 3 "../../../Derivative_LoggingFinest_LoggingBase_SecondaryDatabase.ump"
// line 3 "../../../Derivative_LoggingFinest_LoggingBase_SecondaryDatabase_inner.ump"
public class SecondaryDatabase extends Database
{

  //------------------------
  // MEMBER VARIABLES
  //------------------------

  //------------------------
  // CONSTRUCTOR
  //------------------------

  public SecondaryDatabase(Environment aEnvHandle, DatabaseConfig aConfiguration, Locker aHandleLocker)
  {
    super(aEnvHandle, aConfiguration, aHandleLocker);
  }

  //------------------------
  // INTERFACE
  //------------------------

  public void delete()
  {
    super.delete();
  }


  /**
   * 
   * Creates a secondary database but does not open or fully initialize it.
   */
  // line 31 "../../../SecondaryDatabase.ump"
  public  SecondaryDatabase(Environment env, SecondaryConfig secConfig, Database primaryDatabase) throws DatabaseException{
    super(env);
	DatabaseUtil.checkForNullParam(primaryDatabase, "primaryDatabase");
	primaryDatabase.checkRequiredDbState(OPEN, "Can't use as primary:");
	if (primaryDatabase.configuration.getSortedDuplicates()) {
	    throw new IllegalArgumentException(
		    "Duplicates must not be allowed for a primary database: " + primaryDatabase.getDebugName());
	}
	if (env.getEnvironmentImpl() != primaryDatabase.getEnvironment().getEnvironmentImpl()) {
	    throw new IllegalArgumentException("Primary and secondary databases must be in the same" + " environment");
	}
	if (secConfig.getKeyCreator() != null && secConfig.getMultiKeyCreator() != null) {
	    throw new IllegalArgumentException(
		    "secConfig.getKeyCreator() and getMultiKeyCreator() may not" + " both be non-null");
	}
	if (!primaryDatabase.configuration.getReadOnly() && secConfig.getKeyCreator() == null
		&& secConfig.getMultiKeyCreator() == null) {
	    throw new NullPointerException("secConfig and getKeyCreator()/getMultiKeyCreator()"
		    + " may be null only if the primary database is read-only");
	}
	if (secConfig.getForeignKeyNullifier() != null && secConfig.getForeignMultiKeyNullifier() != null) {
	    throw new IllegalArgumentException("secConfig.getForeignKeyNullifier() and"
		    + " getForeignMultiKeyNullifier() may not both be non-null");
	}
	if (secConfig.getForeignKeyDeleteAction() == ForeignKeyDeleteAction.NULLIFY
		&& secConfig.getForeignKeyNullifier() == null && secConfig.getForeignMultiKeyNullifier() == null) {
	    throw new NullPointerException("ForeignKeyNullifier or ForeignMultiKeyNullifier must be"
		    + " non-null when ForeignKeyDeleteAction is NULLIFY");
	}
	if (secConfig.getForeignKeyNullifier() != null && secConfig.getMultiKeyCreator() != null) {
	    throw new IllegalArgumentException("ForeignKeyNullifier may not be used with"
		    + " SecondaryMultiKeyCreator -- use" + " ForeignMultiKeyNullifier instead");
	}
	if (secConfig.getForeignKeyDatabase() != null) {
	    Database foreignDb = secConfig.getForeignKeyDatabase();
	    if (foreignDb.getDatabaseImpl().getSortedDuplicates()) {
		throw new IllegalArgumentException(
			"Duplicates must not be allowed for a foreign key " + " database: " + foreignDb.getDebugName());
	    }
	}
	primaryDb = primaryDatabase;
	secondaryTrigger = new SecondaryTrigger(this);
	if (secConfig.getForeignKeyDatabase() != null) {
	    foreignKeyTrigger = new ForeignKeyTrigger(this);
	}
  }


  /**
   * 
   * Create a database, called by Environment
   */
  // line 82 "../../../SecondaryDatabase.ump"
  public void initNew(Environment env, Locker locker, String databaseName, DatabaseConfig dbConfig) throws DatabaseException{
    super.initNew(env, locker, databaseName, dbConfig);
	init(locker);
  }


  /**
   * 
   * Open a database, called by Environment
   */
  // line 91 "../../../SecondaryDatabase.ump"
  public void initExisting(Environment env, Locker locker, DatabaseImpl database, DatabaseConfig dbConfig) throws DatabaseException{
    Database otherPriDb = database.findPrimaryDatabase();
	if (otherPriDb != null && otherPriDb.getDatabaseImpl() != primaryDb.getDatabaseImpl()) {
	    throw new IllegalArgumentException(
		    "Secondary is already associated with a different primary: " + otherPriDb.getDebugName());
	}
	super.initExisting(env, locker, database, dbConfig);
	init(locker);
  }


  /**
   * 
   * Adds secondary to primary's list, and populates the secondary if needed.
   */
  // line 104 "../../../SecondaryDatabase.ump"
   private void init(Locker locker) throws DatabaseException{
    // line 16 "../../../LoggingFinest_SecondaryDatabase.ump"
    trace(Level.FINEST, "SecondaryDatabase open");
    	//original(locker);
    // END OF UMPLE BEFORE INJECTION
    secondaryConfig = (SecondaryConfig) configuration;
	primaryDb.addTrigger(secondaryTrigger, false);
	Database foreignDb = secondaryConfig.getForeignKeyDatabase();
	if (foreignDb != null) {
	    foreignDb.addTrigger(foreignKeyTrigger, true);
	}
	if (secondaryConfig.getAllowPopulate()) {
	    Cursor secCursor = null;
	    Cursor priCursor = null;
	    try {
		secCursor = new Cursor(this, locker, null);
		DatabaseEntry key = new DatabaseEntry();
		DatabaseEntry data = new DatabaseEntry();
		OperationStatus status = secCursor.position(key, data, LockMode.DEFAULT, true);
		if (status == OperationStatus.NOTFOUND) {
		    priCursor = new Cursor(primaryDb, locker, null);
		    status = priCursor.position(key, data, LockMode.DEFAULT, true);
		    while (status == OperationStatus.SUCCESS) {
			updateSecondary(locker, secCursor, key, null, data);
			status = priCursor.retrieveNext(key, data, LockMode.DEFAULT, GetMode.NEXT);
		    }
		}
	    } finally {
		if (secCursor != null) {
		    secCursor.close();
		}
		if (priCursor != null) {
		    priCursor.close();
		}
	    }
	}
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 141 "../../../SecondaryDatabase.ump"
   public synchronized  void close() throws DatabaseException{
    if (primaryDb != null && secondaryTrigger != null) {
	    primaryDb.removeTrigger(secondaryTrigger);
	}
	Database foreignDb = secondaryConfig.getForeignKeyDatabase();
	if (foreignDb != null && foreignKeyTrigger != null) {
	    foreignDb.removeTrigger(foreignKeyTrigger);
	}
	super.close();
  }


  /**
   * 
   * Should be called by the secondaryTrigger while holding a write lock on the trigger list.
   */
  // line 155 "../../../SecondaryDatabase.ump"
  public void clearPrimary(){
    primaryDb = null;
	secondaryTrigger = null;
  }


  /**
   * 
   * Should be called by the foreignKeyTrigger while holding a write lock on the trigger list.
   */
  // line 163 "../../../SecondaryDatabase.ump"
  public void clearForeignKeyTrigger(){
    foreignKeyTrigger = null;
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 170 "../../../SecondaryDatabase.ump"
   public Database getPrimaryDatabase() throws DatabaseException{
    return primaryDb;
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 177 "../../../SecondaryDatabase.ump"
   public SecondaryConfig getSecondaryConfig() throws DatabaseException{
    return (SecondaryConfig) getConfig();
  }


  /**
   * 
   * Returns the secondary config without cloning, for internal use.
   */
  // line 184 "../../../SecondaryDatabase.ump"
   public SecondaryConfig getPrivateSecondaryConfig(){
    return secondaryConfig;
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 191 "../../../SecondaryDatabase.ump"
   public SecondaryCursor openSecondaryCursor(Transaction txn, CursorConfig cursorConfig) throws DatabaseException{
    return (SecondaryCursor) openCursor(txn, cursorConfig);
  }


  /**
   * 
   * Overrides Database method.
   */
  // line 198 "../../../SecondaryDatabase.ump"
  public Cursor newDbcInstance(Transaction txn, CursorConfig cursorConfig) throws DatabaseException{
    return new SecondaryCursor(this, txn, cursorConfig);
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 205 "../../../SecondaryDatabase.ump"
   public OperationStatus delete(Transaction txn, DatabaseEntry key) throws DatabaseException{
    checkEnv();
	DatabaseUtil.checkForNullDbt(key, "key", true);
	checkRequiredDbState(OPEN, "Can't call SecondaryDatabase.delete:");
	Label79:
trace(Level.FINEST, "SecondaryDatabase.delete", txn, key, null, null);
	//original(txn, key);
           ;  //this.hook79(txn, key);
	Locker locker = null;
	Cursor cursor = null;
	OperationStatus commitStatus = OperationStatus.NOTFOUND;
	try {
	    locker = LockerFactory.getWritableLocker(envHandle, txn, isTransactional());
	    cursor = new Cursor(this, locker, null);
	    DatabaseEntry pKey = new DatabaseEntry();
	    OperationStatus searchStatus = cursor.search(key, pKey, LockMode.RMW, SearchMode.SET);
	    if (searchStatus == OperationStatus.SUCCESS) {
		commitStatus = primaryDb.deleteInternal(locker, pKey);
	    }
	    return commitStatus;
	} finally {
	    if (cursor != null) {
		cursor.close();
	    }
	    if (locker != null) {
		locker.operationEnd(commitStatus);
	    }
	}
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 236 "../../../SecondaryDatabase.ump"
   public OperationStatus get(Transaction txn, DatabaseEntry key, DatabaseEntry data, LockMode lockMode) throws DatabaseException{
    return get(txn, key, new DatabaseEntry(), data, lockMode);
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 244 "../../../SecondaryDatabase.ump"
   public OperationStatus get(Transaction txn, DatabaseEntry key, DatabaseEntry pKey, DatabaseEntry data, LockMode lockMode) throws DatabaseException{
    checkEnv();
	DatabaseUtil.checkForNullDbt(key, "key", true);
	DatabaseUtil.checkForNullDbt(pKey, "pKey", false);
	DatabaseUtil.checkForNullDbt(data, "data", false);
	checkRequiredDbState(OPEN, "Can't call SecondaryDatabase.get:");
	Label80:
trace(Level.FINEST, "SecondaryDatabase.get", txn, key, null, lockMode);
	//original(txn, key, lockMode);
           ;  //this.hook80(txn, key, lockMode);
	CursorConfig cursorConfig = CursorConfig.DEFAULT;
	if (lockMode == LockMode.READ_COMMITTED) {
	    cursorConfig = CursorConfig.READ_COMMITTED;
	    lockMode = null;
	}
	SecondaryCursor cursor = null;
	try {
	    cursor = new SecondaryCursor(this, txn, cursorConfig);
	    return cursor.search(key, pKey, data, lockMode, SearchMode.SET);
	} finally {
	    if (cursor != null) {
		cursor.close();
	    }
	}
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 271 "../../../SecondaryDatabase.ump"
   public OperationStatus getSearchBoth(Transaction txn, DatabaseEntry key, DatabaseEntry data, LockMode lockMode) throws DatabaseException{
    throw notAllowedException();
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 279 "../../../SecondaryDatabase.ump"
   public OperationStatus getSearchBoth(Transaction txn, DatabaseEntry key, DatabaseEntry pKey, DatabaseEntry data, LockMode lockMode) throws DatabaseException{
    checkEnv();
	DatabaseUtil.checkForNullDbt(key, "key", true);
	DatabaseUtil.checkForNullDbt(pKey, "pKey", true);
	DatabaseUtil.checkForNullDbt(data, "data", false);
	checkRequiredDbState(OPEN, "Can't call SecondaryDatabase.getSearchBoth:");
	Label81:
trace(Level.FINEST, "SecondaryDatabase.getSearchBoth", txn, key, data, lockMode);
	//original(txn, key, data, lockMode);
           ;  //this.hook81(txn, key, data, lockMode);
	CursorConfig cursorConfig = CursorConfig.DEFAULT;
	if (lockMode == LockMode.READ_COMMITTED) {
	    cursorConfig = CursorConfig.READ_COMMITTED;
	    lockMode = null;
	}
	SecondaryCursor cursor = null;
	try {
	    cursor = new SecondaryCursor(this, txn, cursorConfig);
	    return cursor.search(key, pKey, data, lockMode, SearchMode.BOTH);
	} finally {
	    if (cursor != null) {
		cursor.close();
	    }
	}
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 305 "../../../SecondaryDatabase.ump"
   public OperationStatus put(Transaction txn, DatabaseEntry key, DatabaseEntry data) throws DatabaseException{
    throw notAllowedException();
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 313 "../../../SecondaryDatabase.ump"
   public OperationStatus putNoOverwrite(Transaction txn, DatabaseEntry key, DatabaseEntry data) throws DatabaseException{
    throw notAllowedException();
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 321 "../../../SecondaryDatabase.ump"
   public OperationStatus putNoDupData(Transaction txn, DatabaseEntry key, DatabaseEntry data) throws DatabaseException{
    throw notAllowedException();
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 328 "../../../SecondaryDatabase.ump"
   public JoinCursor join(Cursor [] cursors, JoinConfig config) throws DatabaseException{
    throw notAllowedException();
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   * @deprecated
   */
  // line 336 "../../../SecondaryDatabase.ump"
   public int truncate(Transaction txn, boolean countRecords) throws DatabaseException{
    throw notAllowedException();
  }


  /**
   * 
   * Updates a single secondary when a put() or delete() is performed on the primary.
   * @param locker the internal locker.
   * @param cursor secondary cursor to use, or null if this method shouldopen and close a cursor if one is needed.
   * @param priKey the primary key.
   * @param oldData the primary data before the change, or null if the recorddid not previously exist.
   * @param newData the primary data after the change, or null if the recordhas been deleted.
   */
  // line 349 "../../../SecondaryDatabase.ump"
  public void updateSecondary(Locker locker, Cursor cursor, DatabaseEntry priKey, DatabaseEntry oldData, DatabaseEntry newData) throws DatabaseException{
    if (secondaryConfig.getImmutableSecondaryKey() && oldData != null && newData != null) {
	    return;
	}
	SecondaryKeyCreator keyCreator = secondaryConfig.getKeyCreator();
	if (keyCreator != null) {
	    assert secondaryConfig.getMultiKeyCreator() == null;
	    DatabaseEntry oldSecKey = null;
	    if (oldData != null) {
		oldSecKey = new DatabaseEntry();
		if (!keyCreator.createSecondaryKey(this, priKey, oldData, oldSecKey)) {
		    oldSecKey = null;
		}
	    }
	    DatabaseEntry newSecKey = null;
	    if (newData != null) {
		newSecKey = new DatabaseEntry();
		if (!keyCreator.createSecondaryKey(this, priKey, newData, newSecKey)) {
		    newSecKey = null;
		}
	    }
	    if ((oldSecKey != null && !oldSecKey.equals(newSecKey))
		    || (newSecKey != null && !newSecKey.equals(oldSecKey))) {
		boolean localCursor = (cursor == null);
		if (localCursor) {
		    cursor = new Cursor(this, locker, null);
		}
		try {
		    if (oldSecKey != null) {
			deleteKey(cursor, priKey, oldSecKey);
		    }
		    if (newSecKey != null) {
			insertKey(locker, cursor, priKey, newSecKey);
		    }
		} finally {
		    if (localCursor && cursor != null) {
			cursor.close();
		    }
		}
	    }
	} else {
	    SecondaryMultiKeyCreator multiKeyCreator = secondaryConfig.getMultiKeyCreator();
	    assert multiKeyCreator != null;
	    Set oldKeys = Collections.EMPTY_SET;
	    Set newKeys = Collections.EMPTY_SET;
	    if (oldData != null) {
		oldKeys = new HashSet();
		multiKeyCreator.createSecondaryKeys(this, priKey, oldData, oldKeys);
	    }
	    if (newData != null) {
		newKeys = new HashSet();
		multiKeyCreator.createSecondaryKeys(this, priKey, newData, newKeys);
	    }
	    if (!oldKeys.equals(newKeys)) {
		boolean localCursor = (cursor == null);
		if (localCursor) {
		    cursor = new Cursor(this, locker, null);
		}
		try {
		    Set oldKeysCopy = oldKeys;
		    if (oldKeys != Collections.EMPTY_SET) {
			oldKeysCopy = new HashSet(oldKeys);
			oldKeys.removeAll(newKeys);
			for (Iterator i = oldKeys.iterator(); i.hasNext();) {
			    DatabaseEntry oldKey = (DatabaseEntry) i.next();
			    deleteKey(cursor, priKey, oldKey);
			}
		    }
		    if (newKeys != Collections.EMPTY_SET) {
			newKeys.removeAll(oldKeysCopy);
			for (Iterator i = newKeys.iterator(); i.hasNext();) {
			    DatabaseEntry newKey = (DatabaseEntry) i.next();
			    insertKey(locker, cursor, priKey, newKey);
			}
		    }
		} finally {
		    if (localCursor && cursor != null) {
			cursor.close();
		    }
		}
	    }
	}
  }


  /**
   * 
   * Deletes an old secondary key.
   */
  // line 436 "../../../SecondaryDatabase.ump"
   private void deleteKey(Cursor cursor, DatabaseEntry priKey, DatabaseEntry oldSecKey) throws DatabaseException{
    OperationStatus status = cursor.search(oldSecKey, priKey, LockMode.RMW, SearchMode.BOTH);
	if (status == OperationStatus.SUCCESS) {
	    cursor.deleteInternal();
	} else {
	    throw new DatabaseException("Secondary " + getDebugName() + " is corrupt: the primary record contains a key"
		    + " that is not present in the secondary");
	}
  }


  /**
   * 
   * Inserts a new secondary key.
   */
  // line 450 "../../../SecondaryDatabase.ump"
   private void insertKey(Locker locker, Cursor cursor, DatabaseEntry priKey, DatabaseEntry newSecKey) throws DatabaseException{
    Database foreignDb = secondaryConfig.getForeignKeyDatabase();
	if (foreignDb != null) {
	    Cursor foreignCursor = null;
	    try {
		foreignCursor = new Cursor(foreignDb, locker, null);
		DatabaseEntry tmpData = new DatabaseEntry();
		OperationStatus status = foreignCursor.search(newSecKey, tmpData, LockMode.DEFAULT, SearchMode.SET);
		if (status != OperationStatus.SUCCESS) {
		    throw new DatabaseException("Secondary " + getDebugName() + " foreign key not allowed: it is not"
			    + " present in the foreign database");
		}
	    } finally {
		if (foreignCursor != null) {
		    foreignCursor.close();
		}
	    }
	}
	OperationStatus status;
	if (configuration.getSortedDuplicates()) {
	    status = cursor.putInternal(newSecKey, priKey, PutMode.NODUP);
	} else {
	    status = cursor.putInternal(newSecKey, priKey, PutMode.NOOVERWRITE);
	}
	if (status != OperationStatus.SUCCESS) {
	    throw new DatabaseException("Could not insert secondary key in " + getDebugName() + ' ' + status);
	}
  }


  /**
   * 
   * Called by the ForeignKeyTrigger when a record in the foreign database is deleted.
   * @param secKey is the primary key of the foreign database, which is thesecondary key (ordinary key) of this secondary database.
   */
  // line 483 "../../../SecondaryDatabase.ump"
  public void onForeignKeyDelete(Locker locker, DatabaseEntry secKey) throws DatabaseException{
    ForeignKeyDeleteAction deleteAction = secondaryConfig.getForeignKeyDeleteAction();
	LockMode lockMode = (deleteAction == ForeignKeyDeleteAction.ABORT) ? LockMode.DEFAULT : LockMode.RMW;
	DatabaseEntry priKey = new DatabaseEntry();
	Cursor cursor = null;
	OperationStatus status;
	try {
	    cursor = new Cursor(this, locker, null);
	    status = cursor.search(secKey, priKey, lockMode, SearchMode.SET);
	    while (status == OperationStatus.SUCCESS) {
		if (deleteAction == ForeignKeyDeleteAction.ABORT) {
		    throw new DatabaseException("Secondary " + getDebugName()
			    + " refers to a foreign key that has been deleted" + " (ForeignKeyDeleteAction.ABORT)");
		} else if (deleteAction == ForeignKeyDeleteAction.CASCADE) {
		    Cursor priCursor = null;
		    try {
			DatabaseEntry data = new DatabaseEntry();
			priCursor = new Cursor(primaryDb, locker, null);
			status = priCursor.search(priKey, data, LockMode.RMW, SearchMode.SET);
			if (status == OperationStatus.SUCCESS) {
			    priCursor.delete();
			} else {
			    throw secondaryCorruptException();
			}
		    } finally {
			if (priCursor != null) {
			    priCursor.close();
			}
		    }
		} else if (deleteAction == ForeignKeyDeleteAction.NULLIFY) {
		    Cursor priCursor = null;
		    try {
			DatabaseEntry data = new DatabaseEntry();
			priCursor = new Cursor(primaryDb, locker, null);
			status = priCursor.search(priKey, data, LockMode.RMW, SearchMode.SET);
			if (status == OperationStatus.SUCCESS) {
			    ForeignMultiKeyNullifier multiNullifier = secondaryConfig.getForeignMultiKeyNullifier();
			    if (multiNullifier != null) {
				if (multiNullifier.nullifyForeignKey(this, priKey, data, secKey)) {
				    priCursor.putCurrent(data);
				}
			    } else {
				ForeignKeyNullifier nullifier = secondaryConfig.getForeignKeyNullifier();
				if (nullifier.nullifyForeignKey(this, data)) {
				    priCursor.putCurrent(data);
				}
			    }
			} else {
			    throw secondaryCorruptException();
			}
		    } finally {
			if (priCursor != null) {
			    priCursor.close();
			}
		    }
		} else {
		    throw new IllegalStateException();
		}
		status = cursor.retrieveNext(secKey, priKey, LockMode.DEFAULT, GetMode.NEXT_DUP);
	    }
	} finally {
	    if (cursor != null) {
		cursor.close();
	    }
	}
  }

  // line 550 "../../../SecondaryDatabase.ump"
  public DatabaseException secondaryCorruptException() throws DatabaseException{
    throw new DatabaseException(
		"Secondary " + getDebugName() + " is corrupt: it refers" + " to a missing key in the primary database");
  }

  // line 555 "../../../SecondaryDatabase.ump"
   static  UnsupportedOperationException notAllowedException(){
    throw new UnsupportedOperationException("Operation not allowed on a secondary");
  }


  /**
   * 
   * Send trace messages to the java.util.logger. Don't rely on the logger alone to conditionalize whether we send this message, we don't even want to construct the message if the level is not enabled.
   */
  // line 9 "../../../LoggingFinest_SecondaryDatabase.ump"
  public void trace(Level level, String methodName) throws DatabaseException{
    new SecondaryDatabase_trace(this, level, methodName).execute();
  }
  /*PLEASE DO NOT EDIT THIS CODE*/
  /*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/
  
  
  
  // line 4 "../../../LoggingFinest_SecondaryDatabase_inner.ump"
  // line 4 "../../../Derivative_LoggingFinest_LoggingBase_SecondaryDatabase_inner.ump"
  public static class SecondaryDatabase_trace
  {
  
    //------------------------
    // MEMBER VARIABLES
    //------------------------
  
    //------------------------
    // CONSTRUCTOR
    //------------------------
  
    public SecondaryDatabase_trace()
    {}
  
    //------------------------
    // INTERFACE
    //------------------------
  
    public void delete()
    {}
  
    // line 6 "../../../LoggingFinest_SecondaryDatabase_inner.ump"
    public  SecondaryDatabase_trace(SecondaryDatabase _this, Level level, String methodName){
      this._this=_this;
          this.level=level;
          this.methodName=methodName;
    }
  
    // line 11 "../../../LoggingFinest_SecondaryDatabase_inner.ump"
    public void execute() throws DatabaseException{
      // line 6 "../../../Derivative_LoggingFinest_LoggingBase_SecondaryDatabase_inner.ump"
      logger=envHandle.getEnvironmentImpl().getLogger();
              if (logger.isLoggable(level)) {
                sb=new StringBuffer();
                sb.append(methodName);
                sb.append(" name=").append(_this.getDebugName());
                sb.append(" primary=").append(_this.primaryDb.getDebugName());
                logger.log(level,sb.toString());
              }
              //original();
      // END OF UMPLE BEFORE INJECTION
      
    }
    
    //------------------------
    // DEVELOPER CODE - PROVIDED AS-IS
    //------------------------
    
    // line 12 "../../../LoggingFinest_SecondaryDatabase_inner.ump"
    protected SecondaryDatabase _this ;
  // line 13 "../../../LoggingFinest_SecondaryDatabase_inner.ump"
    protected Level level ;
  // line 14 "../../../LoggingFinest_SecondaryDatabase_inner.ump"
    protected String methodName ;
  // line 15 "../../../LoggingFinest_SecondaryDatabase_inner.ump"
    protected Logger logger ;
  // line 16 "../../../LoggingFinest_SecondaryDatabase_inner.ump"
    protected StringBuffer sb ;
  
    
  }  
  //------------------------
  // DEVELOPER CODE - PROVIDED AS-IS
  //------------------------
  
  // line 19 "../../../SecondaryDatabase.ump"
  private Database primaryDb ;
// line 21 "../../../SecondaryDatabase.ump"
  private SecondaryConfig secondaryConfig ;
// line 23 "../../../SecondaryDatabase.ump"
  private SecondaryTrigger secondaryTrigger ;
// line 25 "../../../SecondaryDatabase.ump"
  private ForeignKeyTrigger foreignKeyTrigger ;

  
}