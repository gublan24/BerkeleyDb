/*PLEASE DO NOT EDIT THIS CODE*/
/*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/

package com.sleepycat.je.dbi;
import de.ovgu.cide.jakutil.*;
import com.sleepycat.je.utilint.TestHook;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.CmdUtil;
import com.sleepycat.je.txn.ThreadLocker;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.tree.WithRootLatched;
import com.sleepycat.je.tree.TreeWalkerStatsAccumulator;
import com.sleepycat.je.tree.TreeUtils;
import com.sleepycat.je.tree.Tree;
import com.sleepycat.je.tree.Node;
import com.sleepycat.je.tree.ChildReference;
import com.sleepycat.je.log.LogWritable;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.log.LogReadable;
import com.sleepycat.je.log.LogException;
import com.sleepycat.je.log.LogEntryType;
import com.sleepycat.je.dbi.SortedLSNTreeWalker.TreeNodeProcessor;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.cleaner.UtilizationTracker;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.PreloadStatus;
import com.sleepycat.je.PreloadStats;
import com.sleepycat.je.PreloadConfig;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.Cursor;
import java.util.Set;
import java.util.Map;
import java.util.Iterator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Comparator;
import java.util.Collections;
import java.nio.ByteBuffer;
import java.io.PrintStream;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.log.*;
import com.sleepycat.je.log.entry.*;

// line 3 "../../../../DatabaseImpl.ump"
// line 3 "../../../../DatabaseImpl_static.ump"
// line 3 "../../../../MemoryBudget_DatabaseImpl.ump"
// line 3 "../../../../MemoryBudget_DatabaseImpl_inner.ump"
// line 3 "../../../../DeleteOp_DatabaseImpl.ump"
// line 3 "../../../../Latches_DatabaseImpl.ump"
// line 3 "../../../../Latches_DatabaseImpl_inner.ump"
public class DatabaseImpl implements LogWritable,LogReadable,Cloneable
{

  //------------------------
  // MEMBER VARIABLES
  //------------------------

  //------------------------
  // CONSTRUCTOR
  //------------------------
  // Default constructor has been disabled.  


  //------------------------
  // INTERFACE
  //------------------------

  public void delete()
  {}


  /**
   * 
   * Create a database object for a new database.
   */
  // line 98 "../../../../DatabaseImpl.ump"
   public  DatabaseImpl(String dbName, DatabaseId id, EnvironmentImpl envImpl, DatabaseConfig dbConfig) throws DatabaseException{
    this.id = id;
			this.envImpl = envImpl;
			this.btreeComparator = dbConfig.getBtreeComparator();
			this.duplicateComparator = dbConfig.getDuplicateComparator();
			duplicatesAllowed = dbConfig.getSortedDuplicates();
			transactional = dbConfig.getTransactional();
			maxMainTreeEntriesPerNode = dbConfig.getNodeMaxEntries();
			maxDupTreeEntriesPerNode = dbConfig.getNodeMaxDupTreeEntries();
			initDefaultSettings();
			//this.hook288();
			Label288:
deleteState = NOT_DELETED;
			//original();

			tree = new Tree(this);
			referringHandles = Collections.synchronizedSet(new HashSet());
			eofNodeId = Node.getNextNodeId();
			debugDatabaseName = dbName;
  }


  /**
   * 
   * Create an empty database object for initialization from the log. Note that the rest of the initialization comes from readFromLog(), except for the debugDatabaseName, which is set by the caller.
   */
  // line 119 "../../../../DatabaseImpl.ump"
   public  DatabaseImpl() throws DatabaseException{
    id = new DatabaseId();
			envImpl = null;
			//this.hook289();
			Label289:
deleteState = NOT_DELETED;
			//original();

			tree = new Tree();
			referringHandles = Collections.synchronizedSet(new HashSet());
			eofNodeId = Node.getNextNodeId();
  }

  // line 129 "../../../../DatabaseImpl.ump"
   public void setDebugDatabaseName(String debugName){
    debugDatabaseName = debugName;
  }

  // line 133 "../../../../DatabaseImpl.ump"
   public String getDebugName(){
    return debugDatabaseName;
  }

  // line 137 "../../../../DatabaseImpl.ump"
   public void setPendingDeletedHook(TestHook hook){
    pendingDeletedHook = hook;
  }


  /**
   * 
   * Initialize configuration settings when creating a new instance or after reading an instance from the log. The envImpl field must be set before calling this method.
   */
  // line 144 "../../../../DatabaseImpl.ump"
   private void initDefaultSettings() throws DatabaseException{
    DbConfigManager configMgr = envImpl.getConfigManager();
	binDeltaPercent = configMgr.getInt(EnvironmentParams.BIN_DELTA_PERCENT);
	binMaxDeltas = configMgr.getInt(EnvironmentParams.BIN_MAX_DELTAS);
	if (maxMainTreeEntriesPerNode == 0) {
	    maxMainTreeEntriesPerNode = configMgr.getInt(EnvironmentParams.NODE_MAX);
	}
	if (maxDupTreeEntriesPerNode == 0) {
	    maxDupTreeEntriesPerNode = configMgr.getInt(EnvironmentParams.NODE_MAX_DUPTREE);
	}
  }


  /**
   * 
   * Clone. For now just pass off to the super class for a field-by-field copy.
   */
  // line 159 "../../../../DatabaseImpl.ump"
   public Object clone() throws CloneNotSupportedException{
    return super.clone();
  }


  /**
   * 
   * @return the database tree.
   */
  // line 166 "../../../../DatabaseImpl.ump"
   public Tree getTree(){
    return tree;
  }

  // line 170 "../../../../DatabaseImpl.ump"
  public void setTree(Tree tree){
    this.tree = tree;
  }


  /**
   * 
   * @return the database id.
   */
  // line 177 "../../../../DatabaseImpl.ump"
   public DatabaseId getId(){
    return id;
  }

  // line 181 "../../../../DatabaseImpl.ump"
  public void setId(DatabaseId id){
    this.id = id;
  }

  // line 185 "../../../../DatabaseImpl.ump"
   public long getEofNodeId(){
    return eofNodeId;
  }


  /**
   * 
   * @return true if this database is transactional.
   */
  // line 192 "../../../../DatabaseImpl.ump"
   public boolean isTransactional(){
    return transactional;
  }


  /**
   * 
   * Sets the transactional property for the first opened handle.
   */
  // line 199 "../../../../DatabaseImpl.ump"
   public void setTransactional(boolean transactional){
    this.transactional = transactional;
  }


  /**
   * 
   * @return true if duplicates are allowed in this database.
   */
  // line 206 "../../../../DatabaseImpl.ump"
   public boolean getSortedDuplicates(){
    return duplicatesAllowed;
  }

  // line 210 "../../../../DatabaseImpl.ump"
   public int getNodeMaxEntries(){
    return maxMainTreeEntriesPerNode;
  }

  // line 214 "../../../../DatabaseImpl.ump"
   public int getNodeMaxDupTreeEntries(){
    return maxDupTreeEntriesPerNode;
  }


  /**
   * 
   * Set the duplicate comparison function for this database.
   * @param duplicateComparator -The Duplicate Comparison function.
   */
  // line 222 "../../../../DatabaseImpl.ump"
   public void setDuplicateComparator(Comparator duplicateComparator){
    this.duplicateComparator = duplicateComparator;
  }


  /**
   * 
   * Set the btree comparison function for this database.
   * @param btreeComparator -The btree Comparison function.
   */
  // line 230 "../../../../DatabaseImpl.ump"
   public void setBtreeComparator(Comparator btreeComparator){
    this.btreeComparator = btreeComparator;
  }


  /**
   * 
   * @return the btree Comparator object.
   */
  // line 237 "../../../../DatabaseImpl.ump"
   public Comparator getBtreeComparator(){
    return btreeComparator;
  }


  /**
   * 
   * @return the duplicate Comparator object.
   */
  // line 244 "../../../../DatabaseImpl.ump"
   public Comparator getDuplicateComparator(){
    return duplicateComparator;
  }


  /**
   * 
   * Set the db environment during recovery, after instantiating the database from the log
   */
  // line 251 "../../../../DatabaseImpl.ump"
   public void setEnvironmentImpl(EnvironmentImpl envImpl) throws DatabaseException{
    this.envImpl = envImpl;
	initDefaultSettings();
	tree.setDatabase(this);
  }


  /**
   * 
   * @return the database environment.
   */
  // line 260 "../../../../DatabaseImpl.ump"
   public EnvironmentImpl getDbEnvironment(){
    return envImpl;
  }


  /**
   * 
   * Returns whether one or more handles are open.
   */
  // line 267 "../../../../DatabaseImpl.ump"
   public boolean hasOpenHandles(){
    return referringHandles.size() > 0;
  }


  /**
   * 
   * Add a referring handle
   */
  // line 274 "../../../../DatabaseImpl.ump"
   public void addReferringHandle(Database db){
    referringHandles.add(db);
  }


  /**
   * 
   * Decrement the reference count.
   */
  // line 281 "../../../../DatabaseImpl.ump"
   public void removeReferringHandle(Database db){
    referringHandles.remove(db);
  }


  /**
   * 
   * @return the referring handle count.
   */
  // line 288 "../../../../DatabaseImpl.ump"
   synchronized  int getReferringHandleCount(){
    return referringHandles.size();
  }


  /**
   * 
   * For this secondary database return the primary that it is associated with, or null if not associated with any primary. Note that not all handles need be associated with a primary.
   */
  // line 295 "../../../../DatabaseImpl.ump"
   public Database findPrimaryDatabase() throws DatabaseException{
    for (Iterator i = referringHandles.iterator(); i.hasNext();) {
	    Object obj = i.next();
	    if (obj instanceof SecondaryDatabase) {
		return ((SecondaryDatabase) obj).getPrimaryDatabase();
	    }
	}
	return null;
  }

  // line 305 "../../../../DatabaseImpl.ump"
   public String getName() throws DatabaseException{
    return envImpl.getDbMapTree().getDbName(id);
  }


  /**
   * 
   * Return the count of nodes in the database. Used for truncate, perhaps should be made available through other means? Database should be quiescent.
   */
  // line 312 "../../../../DatabaseImpl.ump"
  public long countRecords() throws DatabaseException{
    LNCounter lnCounter = new LNCounter();
	SortedLSNTreeWalker walker = new SortedLSNTreeWalker(this, false, false, tree.getRootLsn(), lnCounter);
	walker.walk();
	return lnCounter.getCount();
  }

  // line 320 "../../../../DatabaseImpl.ump"
   private boolean walkDatabaseTree(TreeWalkerStatsAccumulator statsAcc, PrintStream out, boolean verbose) throws DatabaseException{
    boolean ok = true;
	Locker locker = new ThreadLocker(envImpl);
	Cursor cursor = null;
	CursorImpl impl = null;
	try {
	    EnvironmentImpl.incThreadLocalReferenceCount();
	    cursor = DbInternal.newCursor(this, locker, null);
	    impl = DbInternal.getCursorImpl(cursor);
	    tree.setTreeStatsAccumulator(statsAcc);
	    impl.setTreeStatsAccumulator(statsAcc);
	    DatabaseEntry foundData = new DatabaseEntry();
	    DatabaseEntry key = new DatabaseEntry();
	    OperationStatus status = DbInternal.position(cursor, key, foundData, LockMode.READ_UNCOMMITTED, true);
	    while (status == OperationStatus.SUCCESS) {
		try {
		    status = DbInternal.retrieveNext(cursor, key, foundData, LockMode.READ_UNCOMMITTED, GetMode.NEXT);
		} catch (DatabaseException DBE) {
		    ok = false;
		    if (DbInternal.advanceCursor(cursor, key, foundData)) {
			if (verbose) {
			    out.println("Error encountered (continuing):");
			    out.println(DBE);
			    printErrorRecord(out, key, foundData);
			}
		    } else {
			throw DBE;
		    }
		}
	    }
	} finally {
	    if (impl != null) {
		impl.setTreeStatsAccumulator(null);
	    }
	    tree.setTreeStatsAccumulator(null);
	    EnvironmentImpl.decThreadLocalReferenceCount();
	    if (cursor != null) {
		cursor.close();
	    }
	}
	return ok;
  }


  /**
   * 
   * Prints the key and data, if available, for a BIN entry that could not be read/verified. Uses the same format as DbDump and prints both the hex and printable versions of the entries.
   */
  // line 366 "../../../../DatabaseImpl.ump"
   private void printErrorRecord(PrintStream out, DatabaseEntry key, DatabaseEntry data){
    byte[] bytes = key.getData();
	StringBuffer sb = new StringBuffer("Error Key ");
	if (bytes == null) {
	    sb.append("UNKNOWN");
	} else {
	    CmdUtil.formatEntry(sb, bytes, false);
	    sb.append(' ');
	    CmdUtil.formatEntry(sb, bytes, true);
	}
	out.println(sb);
	bytes = data.getData();
	sb = new StringBuffer("Error Data ");
	if (bytes == null) {
	    sb.append("UNKNOWN");
	} else {
	    CmdUtil.formatEntry(sb, bytes, false);
	    sb.append(' ');
	    CmdUtil.formatEntry(sb, bytes, true);
	}
	out.println(sb);
  }


  /**
   * 
   * Preload the cache, using up to maxBytes bytes or maxMillsecs msec.
   */
  // line 392 "../../../../DatabaseImpl.ump"
   public PreloadStats preload(PreloadConfig config) throws DatabaseException{
    return new DatabaseImpl_preload(this, config).execute();
  }

  // line 396 "../../../../DatabaseImpl.ump"
   public String dumpString(int nSpaces){
    StringBuffer sb = new StringBuffer();
	sb.append(TreeUtils.indent(nSpaces));
	sb.append("<database id=\"");
	sb.append(id.toString());
	sb.append("\"");
	if (btreeComparator != null) {
	    sb.append(" btc=\"");
	    sb.append(serializeComparator(btreeComparator));
	    sb.append("\"");
	}
	if (duplicateComparator != null) {
	    sb.append(" dupc=\"");
	    sb.append(serializeComparator(duplicateComparator));
	    sb.append("\"");
	}
	sb.append("/>");
	return sb.toString();
  }


  /**
   * 
   * @see LogWritable#getLogSize
   */
  // line 419 "../../../../DatabaseImpl.ump"
   public int getLogSize(){
    return id.getLogSize() + tree.getLogSize() + LogUtils.getBooleanLogSize()
		+ LogUtils.getStringLogSize(serializeComparator(btreeComparator))
		+ LogUtils.getStringLogSize(serializeComparator(duplicateComparator)) + (LogUtils.getIntLogSize() * 2);
  }


  /**
   * 
   * @see LogWritable#writeToLog
   */
  // line 428 "../../../../DatabaseImpl.ump"
   public void writeToLog(ByteBuffer logBuffer){
    id.writeToLog(logBuffer);
	tree.writeToLog(logBuffer);
	LogUtils.writeBoolean(logBuffer, duplicatesAllowed);
	LogUtils.writeString(logBuffer, serializeComparator(btreeComparator));
	LogUtils.writeString(logBuffer, serializeComparator(duplicateComparator));
	LogUtils.writeInt(logBuffer, maxMainTreeEntriesPerNode);
	LogUtils.writeInt(logBuffer, maxDupTreeEntriesPerNode);
  }


  /**
   * 
   * @see LogReadable#readFromLog
   */
  // line 441 "../../../../DatabaseImpl.ump"
   public void readFromLog(ByteBuffer itemBuffer, byte entryTypeVersion) throws LogException{
    id.readFromLog(itemBuffer, entryTypeVersion);
	tree.readFromLog(itemBuffer, entryTypeVersion);
	duplicatesAllowed = LogUtils.readBoolean(itemBuffer);
	btreeComparatorName = LogUtils.readString(itemBuffer);
	duplicateComparatorName = LogUtils.readString(itemBuffer);
	try {
	    if (!EnvironmentImpl.getNoComparators()) {
		if (btreeComparatorName.length() != 0) {
		    Class btreeComparatorClass = Class.forName(btreeComparatorName);
		    btreeComparator = instantiateComparator(btreeComparatorClass, "Btree");
		}
		if (duplicateComparatorName.length() != 0) {
		    Class duplicateComparatorClass = Class.forName(duplicateComparatorName);
		    duplicateComparator = instantiateComparator(duplicateComparatorClass, "Duplicate");
		}
	    }
	} catch (ClassNotFoundException CNFE) {
	    throw new LogException("couldn't instantiate class comparator", CNFE);
	}
	if (entryTypeVersion >= 1) {
	    maxMainTreeEntriesPerNode = LogUtils.readInt(itemBuffer);
	    maxDupTreeEntriesPerNode = LogUtils.readInt(itemBuffer);
	}
  }


  /**
   * 
   * @see LogReadable#dumpLog
   */
  // line 470 "../../../../DatabaseImpl.ump"
   public void dumpLog(StringBuffer sb, boolean verbose){
    sb.append("<database>");
	id.dumpLog(sb, verbose);
	tree.dumpLog(sb, verbose);
	sb.append("<dupsort v=\"").append(duplicatesAllowed);
	sb.append("\"/>");
	sb.append("<btcf name=\"");
	sb.append(btreeComparatorName);
	sb.append("\"/>");
	sb.append("<dupcf name=\"");
	sb.append(duplicateComparatorName);
	sb.append("\"/>");
	sb.append("</database>");
  }


  /**
   * 
   * @see LogReadable#logEntryIsTransactional
   */
  // line 488 "../../../../DatabaseImpl.ump"
   public boolean logEntryIsTransactional(){
    return false;
  }


  /**
   * 
   * @see LogReadable#getTransactionId
   */
  // line 495 "../../../../DatabaseImpl.ump"
   public long getTransactionId(){
    return 0;
  }


  /**
   * 
   * Used both to write to the log and to validate a comparator when set in DatabaseConfig.
   */
  // line 502 "../../../../DatabaseImpl.ump"
   public static  String serializeComparator(Comparator comparator){
    if (comparator != null) {
	    return comparator.getClass().getName();
	} else {
	    return "";
	}
  }


  /**
   * 
   * Used both to read from the log and to validate a comparator when set in DatabaseConfig.
   */
  // line 513 "../../../../DatabaseImpl.ump"
   public static  Comparator instantiateComparator(Class comparator, String comparatorType) throws LogException{
    if (comparator == null) {
	    return null;
	}
	try {
	    return (Comparator) comparator.newInstance();
	} catch (InstantiationException IE) {
	    throw new LogException("Exception while trying to load " + comparatorType + " Comparator class: " + IE);
	} catch (IllegalAccessException IAE) {
	    throw new LogException("Exception while trying to load " + comparatorType + " Comparator class: " + IAE);
	}
  }

  // line 526 "../../../../DatabaseImpl.ump"
   public int getBinDeltaPercent(){
    return binDeltaPercent;
  }

  // line 530 "../../../../DatabaseImpl.ump"
   public int getBinMaxDeltas(){
    return binMaxDeltas;
  }

  // line 16 "../../../../DeleteOp_DatabaseImpl.ump"
   public boolean isDeleted(){
    return !(deleteState == NOT_DELETED);
  }

  // line 20 "../../../../DeleteOp_DatabaseImpl.ump"
   public boolean isDeleteFinished(){
    return (deleteState == DELETED);
  }

  // line 24 "../../../../DeleteOp_DatabaseImpl.ump"
   public void startDeleteProcessing(){
    assert (deleteState == NOT_DELETED);
		deleteState = DELETED_CLEANUP_INLIST_HARVEST;
  }

  // line 29 "../../../../DeleteOp_DatabaseImpl.ump"
  public void finishedINListHarvest(){
    assert (deleteState == DELETED_CLEANUP_INLIST_HARVEST);
		deleteState = DELETED_CLEANUP_LOG_HARVEST;
  }


  /**
   * 
   * Purge a DatabaseImpl and corresponding MapLN in the db mapping tree. Purging consists of removing all related INs from the db mapping tree and deleting the related MapLN. Used at the a transaction end in these cases: - purge the deleted database after a commit of Environment.removeDatabase - purge the deleted database after a commit of Environment.truncateDatabase - purge the newly created database after an abort of Environment.truncateDatabase
   */
  // line 37 "../../../../DeleteOp_DatabaseImpl.ump"
   public void deleteAndReleaseINs() throws DatabaseException{
    startDeleteProcessing();
			releaseDeletedINs();
  }

  // line 42 "../../../../DeleteOp_DatabaseImpl.ump"
   public void releaseDeletedINs() throws DatabaseException{
    if (pendingDeletedHook != null) {
					pendingDeletedHook.doHook();
			}
			try {
					long rootLsn = tree.getRootLsn();
					if (rootLsn == DbLsn.NULL_LSN) {
				envImpl.getDbMapTree().deleteMapLN(id);
					} else {
				UtilizationTracker snapshot = new UtilizationTracker(envImpl);
				snapshot.countObsoleteNodeInexact(rootLsn, LogEntryType.LOG_IN);
				ObsoleteProcessor obsoleteProcessor = new ObsoleteProcessor(snapshot);
				SortedLSNTreeWalker walker = new SortedLSNTreeWalker(this, true, true, rootLsn, obsoleteProcessor);
				envImpl.getDbMapTree().deleteMapLN(id);
				walker.walk();
				envImpl.getUtilizationProfile().countAndLogSummaries(snapshot.getTrackedFiles());
					}
			} finally {
					deleteState = DELETED;
			}
  }

  // line 64 "../../../../DeleteOp_DatabaseImpl.ump"
   public void checkIsDeleted(String operation) throws DatabaseException{
    if (isDeleted()) {
	    throw new DatabaseException("Attempt to " + operation + " a deleted database");
	}
  }
  /*PLEASE DO NOT EDIT THIS CODE*/
  /*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/
  
  
  
  // line 4 "../../../../DatabaseImpl_static.ump"
  public static class ObsoleteProcessor implements TreeNodeProcessor
  {
  
    //------------------------
    // MEMBER VARIABLES
    //------------------------
  
    //------------------------
    // CONSTRUCTOR
    //------------------------
  
    public ObsoleteProcessor()
    {}
  
    //------------------------
    // INTERFACE
    //------------------------
  
    public void delete()
    {}
  
    // line 8 "../../../../DatabaseImpl_static.ump"
    public  ObsoleteProcessor(UtilizationTracker tracker){
      this.tracker=tracker;
    }
  
    // line 11 "../../../../DatabaseImpl_static.ump"
     public void processLSN(long childLsn, LogEntryType childType){
      assert childLsn != DbLsn.NULL_LSN;
          tracker.countObsoleteNodeInexact(childLsn,childType);
    }
    
    //------------------------
    // DEVELOPER CODE - PROVIDED AS-IS
    //------------------------
    
    // line 6 "../../../../DatabaseImpl_static.ump"
    private UtilizationTracker tracker ;
  
    
  }  /*PLEASE DO NOT EDIT THIS CODE*/
  /*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/
  
  
  
  // line 15 "../../../../DatabaseImpl_static.ump"
  public static class LNCounter implements TreeNodeProcessor
  {
  
    //------------------------
    // MEMBER VARIABLES
    //------------------------
  
    //------------------------
    // CONSTRUCTOR
    //------------------------
  
    public LNCounter()
    {}
  
    //------------------------
    // INTERFACE
    //------------------------
  
    public void delete()
    {}
  
    // line 19 "../../../../DatabaseImpl_static.ump"
     public void processLSN(long childLsn, LogEntryType childType){
      assert childLsn != DbLsn.NULL_LSN;
          if (childType.equals(LogEntryType.LOG_LN_TRANSACTIONAL) || childType.equals(LogEntryType.LOG_LN)) {
            counter++;
          }
    }
  
    // line 25 "../../../../DatabaseImpl_static.ump"
    public long getCount(){
      return counter;
    }
    
    //------------------------
    // DEVELOPER CODE - PROVIDED AS-IS
    //------------------------
    
    // line 17 "../../../../DatabaseImpl_static.ump"
    private long counter ;
  
    
  }  /*PLEASE DO NOT EDIT THIS CODE*/
  /*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/
  
  
  
  // line 28 "../../../../DatabaseImpl_static.ump"
  public static class HaltPreloadException extends RuntimeException
  {
  
    //------------------------
    // MEMBER VARIABLES
    //------------------------
  
    //------------------------
    // CONSTRUCTOR
    //------------------------
  
    public HaltPreloadException()
    {
      super();
    }
  
    //------------------------
    // INTERFACE
    //------------------------
  
    public void delete()
    {}
  
    // line 32 "../../../../DatabaseImpl_static.ump"
    public  HaltPreloadException(PreloadStatus status){
      super(status.toString());
          this.status=status;
    }
  
    // line 36 "../../../../DatabaseImpl_static.ump"
    public PreloadStatus getStatus(){
      return status;
    }
    
    //------------------------
    // DEVELOPER CODE - PROVIDED AS-IS
    //------------------------
    
    // line 30 "../../../../DatabaseImpl_static.ump"
    private PreloadStatus status ;
  
    
  }  /*PLEASE DO NOT EDIT THIS CODE*/
  /*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/
  
  
  
  @MethodObject
  // line 39 "../../../../DatabaseImpl_static.ump"
  // line 4 "../../../../MemoryBudget_DatabaseImpl_inner.ump"
  // line 4 "../../../../Latches_DatabaseImpl_inner.ump"
  public static class DatabaseImpl_preload
  {
  
    //------------------------
    // MEMBER VARIABLES
    //------------------------
  
    //------------------------
    // CONSTRUCTOR
    //------------------------
  
    public DatabaseImpl_preload()
    {}
  
    //------------------------
    // INTERFACE
    //------------------------
  
    public void delete()
    {}
  
    // line 41 "../../../../DatabaseImpl_static.ump"
    public  DatabaseImpl_preload(DatabaseImpl _this, PreloadConfig config){
      this._this=_this;
          this.config=config;
    }
  
    // line 45 "../../../../DatabaseImpl_static.ump"
    public PreloadStats execute() throws DatabaseException{
      maxBytes=config.getMaxBytes();
          maxMillisecs=config.getMaxMillisecs();
          targetTime=Long.MAX_VALUE;
          if (maxMillisecs > 0) {
            targetTime=System.currentTimeMillis() + maxMillisecs;
          }
          //this.hook290();
          Label290:
  cacheBudget=_this.envImpl.getMemoryBudget().getCacheBudget();
          if (maxBytes == 0) {
            maxBytes=cacheBudget;
          }
     			else 
        			if (maxBytes > cacheBudget) {
  						throw new IllegalArgumentException("maxBytes parameter to Database.preload() was specified as " + maxBytes + " bytes \nbut the cache is only "+ cacheBudget+ " bytes.");
  						}
  
          //original();
  
          ret=new PreloadStats();
          callback=new PreloadProcessor(_this.envImpl,maxBytes,targetTime,ret);
          walker=new PreloadLSNTreeWalker(_this,callback,config);
          Label287: ; //this.hook287();
          walker.walk();
          //end of hook287
   				Label287_1: ;
          return ret;
    }
    
    //------------------------
    // DEVELOPER CODE - PROVIDED AS-IS
    //------------------------
    
    // line 62 "../../../../DatabaseImpl_static.ump"
    protected DatabaseImpl _this ;
  // line 63 "../../../../DatabaseImpl_static.ump"
    protected PreloadConfig config ;
  // line 64 "../../../../DatabaseImpl_static.ump"
    protected long maxBytes ;
  // line 65 "../../../../DatabaseImpl_static.ump"
    protected long maxMillisecs ;
  // line 66 "../../../../DatabaseImpl_static.ump"
    protected long targetTime ;
  // line 67 "../../../../DatabaseImpl_static.ump"
    protected long cacheBudget ;
  // line 68 "../../../../DatabaseImpl_static.ump"
    protected PreloadStats ret ;
  // line 69 "../../../../DatabaseImpl_static.ump"
    protected PreloadProcessor callback ;
  // line 70 "../../../../DatabaseImpl_static.ump"
    protected SortedLSNTreeWalker walker ;
  
    
  }  
  //------------------------
  // DEVELOPER CODE - PROVIDED AS-IS
  //------------------------
  
  // line 50 "../../../../DatabaseImpl.ump"
  private DatabaseId id ;
// line 52 "../../../../DatabaseImpl.ump"
  protected Tree tree ;
// line 54 "../../../../DatabaseImpl.ump"
  private EnvironmentImpl envImpl ;
// line 56 "../../../../DatabaseImpl.ump"
  private boolean duplicatesAllowed ;
// line 58 "../../../../DatabaseImpl.ump"
  private boolean transactional ;
// line 60 "../../../../DatabaseImpl.ump"
  private Set referringHandles ;
// line 62 "../../../../DatabaseImpl.ump"
  private long eofNodeId ;
// line 64 "../../../../DatabaseImpl.ump"
  private Comparator btreeComparator = null ;
// line 66 "../../../../DatabaseImpl.ump"
  private Comparator duplicateComparator = null ;
// line 68 "../../../../DatabaseImpl.ump"
  private String btreeComparatorName = "" ;
// line 70 "../../../../DatabaseImpl.ump"
  private String duplicateComparatorName = "" ;
// line 72 "../../../../DatabaseImpl.ump"
  private int binDeltaPercent ;
// line 74 "../../../../DatabaseImpl.ump"
  private int binMaxDeltas ;
// line 76 "../../../../DatabaseImpl.ump"
  private int maxMainTreeEntriesPerNode ;
// line 78 "../../../../DatabaseImpl.ump"
  private int maxDupTreeEntriesPerNode ;
// line 80 "../../../../DatabaseImpl.ump"
  private String debugDatabaseName ;
// line 82 "../../../../DatabaseImpl.ump"
  private TestHook pendingDeletedHook ;
// line 87 "../../../../DatabaseImpl.ump"
  static final HaltPreloadException timeExceededPreloadException = new HaltPreloadException(
	    PreloadStatus.EXCEEDED_TIME) ;
// line 90 "../../../../DatabaseImpl.ump"
  static final HaltPreloadException memoryExceededPreloadException = new HaltPreloadException(
	    PreloadStatus.FILLED_CACHE) ;
// line 5 "../../../../DeleteOp_DatabaseImpl.ump"
  private static final short NOT_DELETED = 1 ;
// line 7 "../../../../DeleteOp_DatabaseImpl.ump"
  private static final short DELETED_CLEANUP_INLIST_HARVEST = 2 ;
// line 9 "../../../../DeleteOp_DatabaseImpl.ump"
  private static final short DELETED_CLEANUP_LOG_HARVEST = 3 ;
// line 11 "../../../../DeleteOp_DatabaseImpl.ump"
  private static final short DELETED = 4 ;
// line 13 "../../../../DeleteOp_DatabaseImpl.ump"
  private short deleteState ;

  
}