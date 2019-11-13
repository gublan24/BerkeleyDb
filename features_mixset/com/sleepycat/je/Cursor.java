/*PLEASE DO NOT EDIT THIS CODE*/
/*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/

package com.sleepycat.je;
import de.ovgu.cide.jakutil.*;
import com.sleepycat.je.utilint.InternalException;
import com.sleepycat.je.txn.LockerFactory;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.txn.LockType;
import com.sleepycat.je.txn.BuddyLocker;
import com.sleepycat.je.tree.Node;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.tree.Key;
import com.sleepycat.je.tree.DBIN;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.dbi.CursorImpl.SearchMode;
import com.sleepycat.je.dbi.CursorImpl.KeyChangeStatus;
import com.sleepycat.je.dbi.RangeRestartException;
import com.sleepycat.je.dbi.PutMode;
import com.sleepycat.je.dbi.GetMode;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.dbi.CursorImpl;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.sleepycat.je.latch.LatchSupport;

// line 3 "../../../Cursor.ump"
// line 3 "../../../loggingBase_Cursor.ump"
// line 3 "../../../Latches_Cursor.ump"
// line 3 "../../../LoggingFinest_Cursor.ump"
// line 3 "../../../LoggingFinest_Cursor_inner.ump"
// line 3 "../../../Derivative_LoggingFinest_LoggingBase_Cursor.ump"
// line 3 "../../../Derivative_LoggingFinest_LoggingBase_Cursor_inner.ump"
public class Cursor
{

  //------------------------
  // MEMBER VARIABLES
  //------------------------

  //Cursor Attributes
  private CursorImpl cursorImpl;
  private CursorConfig config;

  //------------------------
  // CONSTRUCTOR
  //------------------------

  public Cursor(CursorImpl aCursorImpl, CursorConfig aConfig)
  {
    cursorImpl = aCursorImpl;
    config = aConfig;
  }

  //------------------------
  // INTERFACE
  //------------------------

  public boolean setCursorImpl(CursorImpl aCursorImpl)
  {
    boolean wasSet = false;
    cursorImpl = aCursorImpl;
    wasSet = true;
    return wasSet;
  }

  public boolean setConfig(CursorConfig aConfig)
  {
    boolean wasSet = false;
    config = aConfig;
    wasSet = true;
    return wasSet;
  }

  /**
   * 
   * The underlying cursor.
   */
  public CursorImpl getCursorImpl()
  {
    return cursorImpl;
  }

  /**
   * 
   * The CursorConfig used to configure this cursor.
   */
  public CursorConfig getConfig()
  {
    return config;
  }

  public void delete()
  {}


  /**
   * 
   * Creates a cursor for a given user transaction. <p>If txn is null, a non-transactional cursor will be created that releases locks for the prior operation when the next operation suceeds.</p>
   */
  // line 57 "../../../Cursor.ump"
  public  Cursor(Database dbHandle, Transaction txn, CursorConfig cursorConfig) throws DatabaseException{
    if (cursorConfig == null) {
            cursorConfig = CursorConfig.DEFAULT;
        }
        Locker locker = LockerFactory.getReadableLocker(dbHandle.getEnvironment(), txn, dbHandle.isTransactional(),
            false, cursorConfig.getReadCommitted());
        init(dbHandle, dbHandle.getDatabaseImpl(), locker, dbHandle.isWritable(), cursorConfig);
  }


  /**
   * 
   * Creates a cursor for a given locker. <p>If locker is null or is non-transactional, a non-transactional cursor will be created that releases locks for the prior operation when the next operation suceeds.</p>
   */
  // line 69 "../../../Cursor.ump"
  public  Cursor(Database dbHandle, Locker locker, CursorConfig cursorConfig) throws DatabaseException{
    if (cursorConfig == null) {
            cursorConfig = CursorConfig.DEFAULT;
        }
        locker = LockerFactory.getReadableLocker(dbHandle.getEnvironment(), dbHandle, locker, false,
            cursorConfig.getReadCommitted());
        init(dbHandle, dbHandle.getDatabaseImpl(), locker, dbHandle.isWritable(), cursorConfig);
  }


  /**
   * 
   * Creates a cursor for a given locker and no db handle. <p>The locker parameter must be non-null.  With this constructor, we use the given locker without applying any special rules for different isolation levels -- the caller must supply the correct locker.</p>
   */
  // line 81 "../../../Cursor.ump"
  public  Cursor(DatabaseImpl dbImpl, Locker locker, CursorConfig cursorConfig) throws DatabaseException{
    if (cursorConfig == null) {
            cursorConfig = CursorConfig.DEFAULT;
        }
        init(null, dbImpl, locker, true, cursorConfig);
  }

  // line 89 "../../../Cursor.ump"
   private void init(Database dbHandle, DatabaseImpl dbImpl, Locker locker, boolean isWritable, CursorConfig cursorConfig) throws DatabaseException{
    assert locker != null;
        assert dbImpl != null;
        cursorImpl = new CursorImpl(dbImpl, locker, false);
        readUncommittedDefault = cursorConfig.getReadUncommitted() || locker.isReadUncommittedDefault();
        serializableIsolationDefault = cursorImpl.getLocker().isSerializableIsolation();
        updateOperationsProhibited = (dbImpl.isTransactional() && !locker.isTransactional()) || !isWritable;
        this.dbImpl = dbImpl;
        this.dbHandle = dbHandle;
        if (dbHandle != null) {
            dbHandle.addCursor(this);
        }
        this.config = cursorConfig;
        //Label:           ;  //this.hook36(dbImpl);
        Label36:
this.logger = dbImpl.getDbEnvironment().getLogger();
	//original(dbImpl);

  }


  /**
   * 
   * Copy constructor.
   */
  // line 109 "../../../Cursor.ump"
  public  Cursor(Cursor cursor, boolean samePosition) throws DatabaseException{
    readUncommittedDefault = cursor.readUncommittedDefault;
        serializableIsolationDefault = cursor.serializableIsolationDefault;
        updateOperationsProhibited = cursor.updateOperationsProhibited;
        cursorImpl = cursor.cursorImpl.dup(samePosition);
        dbImpl = cursor.dbImpl;
        dbHandle = cursor.dbHandle;
        if (dbHandle != null) {
            dbHandle.addCursor(this);
        }
        config = cursor.config;
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 132 "../../../Cursor.ump"
   public Database getDatabase(){
    return dbHandle;
  }


  /**
   * 
   * Always returns non-null, while getDatabase() returns null if no handle is associated with this cursor.
   */
  // line 139 "../../../Cursor.ump"
  public DatabaseImpl getDatabaseImpl(){
    return dbImpl;
  }

  // line 150 "../../../Cursor.ump"
  public void setNonCloning(boolean nonCloning){
    cursorImpl.setNonCloning(nonCloning);
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 157 "../../../Cursor.ump"
   public synchronized  void close() throws DatabaseException{
    checkState(false);
        cursorImpl.close();
        if (dbHandle != null) {
            dbHandle.removeCursor(this);
        }
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 168 "../../../Cursor.ump"
   public int count() throws DatabaseException{
    checkState(true);
        Label0:
trace(Level.FINEST, "Cursor.count: ", null);
	//original();
 //Label:           ;  //this.hook0();
        return countInternal(null);
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 177 "../../../Cursor.ump"
   public Cursor dup(boolean samePosition) throws DatabaseException{
    checkState(false);
        return new Cursor(this, samePosition);
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 185 "../../../Cursor.ump"
   public OperationStatus delete() throws DatabaseException{
    checkState(true);
        checkUpdatesAllowed("delete");
        Label1:
trace(Level.FINEST, "Cursor.delete: ", null);
	//original();
 //Label:           ;  //this.hook1();
        return deleteInternal();
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 195 "../../../Cursor.ump"
   public OperationStatus put(DatabaseEntry key, DatabaseEntry data) throws DatabaseException{
    checkState(false);
        DatabaseUtil.checkForNullDbt(key, "key", true);
        DatabaseUtil.checkForNullDbt(data, "data", true);
        DatabaseUtil.checkForPartialKey(key);
        checkUpdatesAllowed("put");
        Label2:
trace(Level.FINEST, "Cursor.put: ", key, data, null);
	//original(key, data);
 //Label:           ;  //this.hook2(key, data);
        return putInternal(key, data, PutMode.OVERWRITE);
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 208 "../../../Cursor.ump"
   public OperationStatus putNoOverwrite(DatabaseEntry key, DatabaseEntry data) throws DatabaseException{
    checkState(false);
        DatabaseUtil.checkForNullDbt(key, "key", true);
        DatabaseUtil.checkForNullDbt(data, "data", true);
        DatabaseUtil.checkForPartialKey(key);
        checkUpdatesAllowed("putNoOverwrite");
        Label3:
trace(Level.FINEST, "Cursor.putNoOverwrite: ", key, data, null);
	//original(key, data);
 //Label:           ;  //this.hook3(key, data);
        return putInternal(key, data, PutMode.NOOVERWRITE);
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 221 "../../../Cursor.ump"
   public OperationStatus putNoDupData(DatabaseEntry key, DatabaseEntry data) throws DatabaseException{
    checkState(false);
        DatabaseUtil.checkForNullDbt(key, "key", true);
        DatabaseUtil.checkForNullDbt(data, "data", true);
        DatabaseUtil.checkForPartialKey(key);
        checkUpdatesAllowed("putNoDupData");
        Label4:
trace(Level.FINEST, "Cursor.putNoDupData: ", key, data, null);
	//original(key, data);
           ;  //this.hook4(key, data);
        return putInternal(key, data, PutMode.NODUP);
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 234 "../../../Cursor.ump"
   public OperationStatus putCurrent(DatabaseEntry data) throws DatabaseException{
    checkState(true);
        DatabaseUtil.checkForNullDbt(data, "data", true);
        checkUpdatesAllowed("putCurrent");
        Label5:
trace(Level.FINEST, "Cursor.putCurrent: ", null, data, null);
	//original(data);
           ;  //this.hook5(data);
        return putInternal(null, data, PutMode.CURRENT);
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 246 "../../../Cursor.ump"
   public OperationStatus getCurrent(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) throws DatabaseException{
    checkState(true);
        checkArgsNoValRequired(key, data);
        Label6:
trace(Level.FINEST, "Cursor.getCurrent: ", lockMode);
	//original(lockMode);
           ;  //this.hook6(lockMode);
        return getCurrentInternal(key, data, lockMode);
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 256 "../../../Cursor.ump"
   public OperationStatus getFirst(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) throws DatabaseException{
    checkState(false);
        checkArgsNoValRequired(key, data);
        Label7:
trace(Level.FINEST, "Cursor.getFirst: ", lockMode);
	//original(lockMode);
           ;  //this.hook7(lockMode);
        return position(key, data, lockMode, true);
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 266 "../../../Cursor.ump"
   public OperationStatus getLast(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) throws DatabaseException{
    checkState(false);
        checkArgsNoValRequired(key, data);
        Label8:
trace(Level.FINEST, "Cursor.getLast: ", lockMode);
	//original(lockMode);
           ;  //this.hook8(lockMode);
        return position(key, data, lockMode, false);
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 276 "../../../Cursor.ump"
   public OperationStatus getNext(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) throws DatabaseException{
    checkState(false);
        checkArgsNoValRequired(key, data);
        Label9:
trace(Level.FINEST, "Cursor.getNext: ", lockMode);
	//original(lockMode);
           ;  //this.hook9(lockMode);
        if (cursorImpl.isNotInitialized()) {
            return position(key, data, lockMode, true);
        } else {
            return retrieveNext(key, data, lockMode, GetMode.NEXT);
        }
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 291 "../../../Cursor.ump"
   public OperationStatus getNextDup(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) throws DatabaseException{
    checkState(true);
        checkArgsNoValRequired(key, data);
        Label10:
trace(Level.FINEST, "Cursor.getNextDup: ", lockMode);
	//original(lockMode);
           ;  //this.hook10(lockMode);
        return retrieveNext(key, data, lockMode, GetMode.NEXT_DUP);
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 302 "../../../Cursor.ump"
   public OperationStatus getNextNoDup(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) throws DatabaseException{
    checkState(false);
        checkArgsNoValRequired(key, data);
        Label11:
trace(Level.FINEST, "Cursor.getNextNoDup: ", lockMode);
	//original(lockMode);
           ;  //this.hook11(lockMode);
        if (cursorImpl.isNotInitialized()) {
            return position(key, data, lockMode, true);
        } else {
            return retrieveNext(key, data, lockMode, GetMode.NEXT_NODUP);
        }
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 316 "../../../Cursor.ump"
   public OperationStatus getPrev(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) throws DatabaseException{
    checkState(false);
        checkArgsNoValRequired(key, data);
        Label12:
trace(Level.FINEST, "Cursor.getPrev: ", lockMode);
	//original(lockMode);
           ;  //this.hook12(lockMode);
        if (cursorImpl.isNotInitialized()) {
            return position(key, data, lockMode, false);
        } else {
            return retrieveNext(key, data, lockMode, GetMode.PREV);
        }
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 331 "../../../Cursor.ump"
   public OperationStatus getPrevDup(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) throws DatabaseException{
    checkState(true);
        checkArgsNoValRequired(key, data);
        Label13:
trace(Level.FINEST, "Cursor.getPrevDup: ", lockMode);
	//original(lockMode);
           ;  //this.hook13(lockMode);
        return retrieveNext(key, data, lockMode, GetMode.PREV_DUP);
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 342 "../../../Cursor.ump"
   public OperationStatus getPrevNoDup(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) throws DatabaseException{
    checkState(false);
        checkArgsNoValRequired(key, data);
        Label14:
trace(Level.FINEST, "Cursor.getPrevNoDup: ", lockMode);
	//original(lockMode);
           ;  //this.hook14(lockMode);
        if (cursorImpl.isNotInitialized()) {
            return position(key, data, lockMode, false);
        } else {
            return retrieveNext(key, data, lockMode, GetMode.PREV_NODUP);
        }
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 357 "../../../Cursor.ump"
   public OperationStatus getSearchKey(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) throws DatabaseException{
    checkState(false);
        DatabaseUtil.checkForNullDbt(key, "key", true);
        DatabaseUtil.checkForNullDbt(data, "data", false);
        Label15:
trace(Level.FINEST, "Cursor.getSearchKey: ", key, null, lockMode);
	//original(key, lockMode);
           ;  //this.hook15(key, lockMode);
        return search(key, data, lockMode, SearchMode.SET);
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 369 "../../../Cursor.ump"
   public OperationStatus getSearchKeyRange(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) throws DatabaseException{
    checkState(false);
        DatabaseUtil.checkForNullDbt(key, "key", true);
        DatabaseUtil.checkForNullDbt(data, "data", false);
        Label16:
trace(Level.FINEST, "Cursor.getSearchKeyRange: ", key, null, lockMode);
	//original(key, lockMode);
           ;  //this.hook16(key, lockMode);
        return search(key, data, lockMode, SearchMode.SET_RANGE);
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 381 "../../../Cursor.ump"
   public OperationStatus getSearchBoth(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) throws DatabaseException{
    checkState(false);
        checkArgsValRequired(key, data);
        Label17:
trace(Level.FINEST, "Cursor.getSearchBoth: ", key, data, lockMode);
	//original(key, data, lockMode);
           ;  //this.hook17(key, data, lockMode);
        return search(key, data, lockMode, SearchMode.BOTH);
  }


  /**
   * 
   * Javadoc for this public method is generated via the doc templates in the doc_src directory.
   */
  // line 392 "../../../Cursor.ump"
   public OperationStatus getSearchBothRange(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) throws DatabaseException{
    checkState(false);
        checkArgsValRequired(key, data);
        Label18:
trace(Level.FINEST, "Cursor.getSearchBothRange: ", key, data, lockMode);
	//original(key, data, lockMode);
           ;  //this.hook18(key, data, lockMode);
        return search(key, data, lockMode, SearchMode.BOTH_RANGE);
  }


  /**
   * 
   * Counts duplicates without parameter checking.
   */
  // line 402 "../../../Cursor.ump"
  public int countInternal(LockMode lockMode) throws DatabaseException{
    CursorImpl origCursor = null;
        CursorImpl dup = null;
        try {
            origCursor = cursorImpl;
            dup = origCursor.cloneCursor(true);
            return dup.count(getLockType(lockMode, false));
        } finally {
            if (dup != origCursor) {
                dup.close();
            }
        }
  }


  /**
   * 
   * Internal version of delete() that does no parameter checking.  Calls deleteNoNotify() and notifies triggers (performs secondary updates).
   */
  // line 419 "../../../Cursor.ump"
  public OperationStatus deleteInternal() throws DatabaseException{
    DatabaseEntry oldKey = null;
        DatabaseEntry oldData = null;
        boolean doNotifyTriggers = dbHandle != null && dbHandle.hasTriggers();
        if (doNotifyTriggers) {
            oldKey = new DatabaseEntry();
            oldData = new DatabaseEntry();
            OperationStatus status = getCurrentInternal(oldKey, oldData, LockMode.RMW);
            if (status != OperationStatus.SUCCESS) {
                return OperationStatus.KEYEMPTY;
            }
        }
        if (doNotifyTriggers) {
            dbHandle.notifyTriggers(cursorImpl.getLocker(), oldKey, oldData, null);
        }
        OperationStatus status = deleteNoNotify();
        return status;
  }


  /**
   * 
   * Clone the cursor, delete at current position, and if successful, swap cursors.  Does not notify triggers (does not perform secondary updates).
   */
  // line 441 "../../../Cursor.ump"
  public OperationStatus deleteNoNotify() throws DatabaseException{
    CursorImpl origCursor = null;
        CursorImpl dup = null;
        OperationStatus status = OperationStatus.KEYEMPTY;
        try {
            origCursor = cursorImpl;
            dup = origCursor.cloneCursor(true);
            Label19:
dup.latchBINs();
			//original(dup);
 //Label:           ;  //this.hook19(dup);
                status = dup.delete();
            return status;
        } finally {
            Label20:
if (origCursor != null) {
					origCursor.releaseBINs();
			}
			if (dup != null) {
					dup.releaseBINs();
			}
			//original(origCursor, dup);
 //Label:           ;  //this.hook20(origCursor, dup);
                boolean success = (status == OperationStatus.SUCCESS);
            if (cursorImpl == dup) {
                if (!success) {
                    cursorImpl.reset();
                }
            } else {
                if (success) {
                    origCursor.close();
                    cursorImpl = dup;
                } else {
                    dup.close();
                }
            }
        }
  }


  /**
   * 
   * Internal version of put() that does no parameter checking.  Calls putNoNotify() and notifies triggers (performs secondary updates). Prevents phantoms.
   */
  // line 472 "../../../Cursor.ump"
  public OperationStatus putInternal(DatabaseEntry key, DatabaseEntry data, PutMode putMode) throws DatabaseException{
    DatabaseEntry oldData = null;
        boolean doNotifyTriggers = dbHandle != null && dbHandle.hasTriggers();
        if (doNotifyTriggers && (putMode == PutMode.CURRENT || putMode == PutMode.OVERWRITE)) {
            oldData = new DatabaseEntry();
            if (key == null && putMode == PutMode.CURRENT) {
                key = new DatabaseEntry();
            }
        }
        OperationStatus commitStatus = putNoNotify(key, data, putMode, oldData);
        if (doNotifyTriggers && commitStatus == OperationStatus.SUCCESS) {
            if (oldData != null && oldData.getData() == null) {
                oldData = null;
            }
            dbHandle.notifyTriggers(cursorImpl.getLocker(), key, oldData, data);
        }
        return commitStatus;
  }


  /**
   * 
   * Performs the put operation but does not notify triggers (does not perform secondary updates).  Prevents phantoms.
   */
  // line 495 "../../../Cursor.ump"
  public OperationStatus putNoNotify(DatabaseEntry key, DatabaseEntry data, PutMode putMode, DatabaseEntry returnOldData) throws DatabaseException{
    Locker nextKeyLocker = null;
        CursorImpl nextKeyCursor = null;
        try {
            Locker cursorLocker = cursorImpl.getLocker();
            if (putMode != PutMode.CURRENT &&
                dbImpl.getDbEnvironment().getTxnManager().areOtherSerializableTransactionsActive(cursorLocker)) {
                nextKeyLocker = new BuddyLocker(dbImpl.getDbEnvironment(), cursorLocker);
                nextKeyCursor = new CursorImpl(dbImpl, nextKeyLocker);
                nextKeyCursor.lockNextKeyForInsert(key, data);
            }
            return putAllowPhantoms(key, data, putMode, returnOldData, nextKeyCursor);
        } finally {
            if (nextKeyCursor != null) {
                nextKeyCursor.close();
            }
            if (nextKeyLocker != null) {
                nextKeyLocker.operationEnd();
            }
        }
  }


  /**
   * 
   * Clone the cursor, put key/data according to PutMode, and if successful, swap cursors.  Does not notify triggers (does not perform secondary updates).  Does not prevent phantoms.
   * @param nextKeyCursor is the cursor used to lock the next key duringphantom prevention.  If this cursor is non-null and initialized, it's BIN will be used to initialize the dup cursor used to perform insertion. This enables an optimization that skips the search for the BIN.
   */
  // line 522 "../../../Cursor.ump"
   private OperationStatus putAllowPhantoms(DatabaseEntry key, DatabaseEntry data, PutMode putMode, DatabaseEntry returnOldData, CursorImpl nextKeyCursor) throws DatabaseException{
    if (data == null) {
            throw new NullPointerException("put passed a null DatabaseEntry arg");
        }
        if (putMode != PutMode.CURRENT && key == null) {
            throw new IllegalArgumentException("put passed a null DatabaseEntry arg");
        }
        CursorImpl origCursor = null;
        OperationStatus status = OperationStatus.NOTFOUND;
        CursorImpl dup = null;
        try {
            origCursor = cursorImpl;
            if (putMode == PutMode.CURRENT) {
                dup = origCursor.cloneCursor(true);
            } else {
                dup = origCursor.cloneCursor(false, nextKeyCursor);
            }
            if (putMode == PutMode.CURRENT) {
                status = dup.putCurrent(data, key, returnOldData);
            } else if (putMode == PutMode.OVERWRITE) {
                status = dup.put(key, data, returnOldData);
            } else if (putMode == PutMode.NOOVERWRITE) {
                status = dup.putNoOverwrite(key, data);
            } else if (putMode == PutMode.NODUP) {
                status = dup.putNoDupData(key, data);
            } else {
                throw new InternalException("unknown PutMode");
            }
            return status;
        } finally {
            Label21:
if (origCursor != null) {
					origCursor.releaseBINs();
			}
			//original(origCursor);
 //Label:           ;  //this.hook21(origCursor);
                boolean success = (status == OperationStatus.SUCCESS);
            if (cursorImpl == dup) {
                if (!success) {
                    cursorImpl.reset();
                }
            } else {
                if (success) {
                    origCursor.close();
                    cursorImpl = dup;
                } else {
                    if (dup != null) {
                        dup.close();
                    }
                }
            }
        }
  }


  /**
   * 
   * Position the cursor at the first or last record of the database. Prevents phantoms.
   */
  // line 575 "../../../Cursor.ump"
  public OperationStatus position(DatabaseEntry key, DatabaseEntry data, LockMode lockMode, boolean first) throws DatabaseException{
    if (!isSerializableIsolation(lockMode)) {
            return positionAllowPhantoms(key, data, getLockType(lockMode, false), first);
        }
        while (true) {
            try {
                if (!first) {
                    cursorImpl.lockEofNode(LockType.RANGE_READ);
                }
                LockType lockType = getLockType(lockMode, first);
                OperationStatus status = positionAllowPhantoms(key, data, lockType, first);
                if (first && status != OperationStatus.SUCCESS) {
                    cursorImpl.lockEofNode(LockType.RANGE_READ);
                }
                return status;
            } catch (RangeRestartException e) {
                continue;
            }
        }
  }


  /**
   * 
   * Position without preventing phantoms.
   */
  // line 600 "../../../Cursor.ump"
   private OperationStatus positionAllowPhantoms(DatabaseEntry key, DatabaseEntry data, LockType lockType, boolean first) throws DatabaseException{
    assert(key != null && data != null);
        OperationStatus status = OperationStatus.NOTFOUND;
        CursorImpl dup = null;
        try {
            dup = beginRead(false);
            if (!dup.positionFirstOrLast(first, null)) {
                status = OperationStatus.NOTFOUND;
                Label22:
assert LatchSupport.countLatchesHeld() == 0 : LatchSupport.latchesHeldToString();
      	//original();
 //Label:           ;  //this.hook22();
            } else {
                Label23:
assert LatchSupport.countLatchesHeld() == 1 : LatchSupport.latchesHeldToString();
				//original();
 //Label:           ;  //this.hook23();
                    status = dup.getCurrentAlreadyLatched(key, data, lockType, first);
                if (status == OperationStatus.SUCCESS) {
                    if (dup.getDupBIN() != null) {
                        dup.incrementLNCount();
                    }
                } else {
                    status = dup.getNext(key, data, lockType, first, false);
                }
            }
        } finally {
            Label24:
cursorImpl.releaseBINs();
			//original();
 //Label:           ;  //this.hook24();
                endRead(dup, status == OperationStatus.SUCCESS);
        }
        return status;
  }


  /**
   * 
   * Perform search by key, data, or both.  Prevents phantoms.
   */
  // line 631 "../../../Cursor.ump"
  public OperationStatus search(DatabaseEntry key, DatabaseEntry data, LockMode lockMode, SearchMode searchMode) throws DatabaseException{
    if (!isSerializableIsolation(lockMode)) {
            LockType lockType = getLockType(lockMode, false);
            KeyChangeStatus result = searchAllowPhantoms(key, data, lockType, lockType, searchMode);
            return result.status;
        }
        while (true) {
            try {
                LockType searchLockType = getLockType(lockMode, false);
                LockType advanceLockType = getLockType(lockMode, true);
                DatabaseEntry tryKey = new DatabaseEntry(key.getData(), key.getOffset(), key.getSize());
                DatabaseEntry tryData = new DatabaseEntry(data.getData(), data.getOffset(), data.getSize());
                KeyChangeStatus result;
                if (searchMode.isExactSearch()) {
                    result = searchExactAndRangeLock(tryKey, tryData, searchLockType, advanceLockType, searchMode);
                } else {
                    result = searchAllowPhantoms(tryKey, tryData, searchLockType, advanceLockType, searchMode);
                    if (result.status != OperationStatus.SUCCESS) {
                        cursorImpl.lockEofNode(LockType.RANGE_READ);
                    }
                }
                if (result.status == OperationStatus.SUCCESS) {
                    key.setData(tryKey.getData(), 0, tryKey.getSize());
                    data.setData(tryData.getData(), 0, tryData.getSize());
                }
                return result.status;
            } catch (RangeRestartException e) {
                continue;
            }
        }
  }


  /**
   * 
   * For an exact search, perform a range search and return NOTFOUND if the key changes (or if the data changes for BOTH) during the search. If no exact match is found the range search will range lock the following key for phantom prevention.  Importantly, the cursor position is not changed if an exact match is not found, even though we advance to the following key in order to range lock it.
   */
  // line 667 "../../../Cursor.ump"
   private KeyChangeStatus searchExactAndRangeLock(DatabaseEntry key, DatabaseEntry data, LockType searchLockType, LockType advanceLockType, SearchMode searchMode) throws DatabaseException{
    searchMode = (searchMode == SearchMode.SET) ? SearchMode.SET_RANGE : SearchMode.BOTH_RANGE;
        KeyChangeStatus result = null;
        boolean noNextKeyFound;
        CursorImpl dup = beginRead(false);
        try {
            result = searchInternal(dup, key, data, searchLockType, advanceLockType, searchMode, true);
            noNextKeyFound = !result.keyChange;
            if (result.keyChange && result.status == OperationStatus.SUCCESS) {
                result.status = OperationStatus.NOTFOUND;
            }
        } finally {
            endRead(dup, result != null && result.status == OperationStatus.SUCCESS);
        }
        if (noNextKeyFound) {
            cursorImpl.lockEofNode(LockType.RANGE_READ);
        }
        return result;
  }


  /**
   * 
   * Perform search without preventing phantoms.
   */
  // line 691 "../../../Cursor.ump"
   private KeyChangeStatus searchAllowPhantoms(DatabaseEntry key, DatabaseEntry data, LockType searchLockType, LockType advanceLockType, SearchMode searchMode) throws DatabaseException{
    OperationStatus status = OperationStatus.NOTFOUND;
        CursorImpl dup = beginRead(false);
        try {
            KeyChangeStatus result = searchInternal(dup, key, data, searchLockType, advanceLockType, searchMode, false);
            status = result.status;
            return result;
        } finally {
            endRead(dup, status == OperationStatus.SUCCESS);
        }
  }


  /**
   * 
   * Perform search for a given CursorImpl.
   */
  // line 708 "../../../Cursor.ump"
   private KeyChangeStatus searchInternal(CursorImpl dup, DatabaseEntry key, DatabaseEntry data, LockType searchLockType, LockType advanceLockType, SearchMode searchMode, boolean advanceAfterRangeSearch) throws DatabaseException{
    OperationStatus status = OperationStatus.NOTFOUND;
        boolean keyChange = false;
        try {
            assert key != null && data != null;
        } finally {
            Label25:
cursorImpl.releaseBINs();
			if (status != OperationStatus.SUCCESS && dup != cursorImpl) {
				dup.releaseBINs();								
			}
 //Label:           ;  //this.hook25(dup, key, data, searchLockType, advanceLockType, searchMode, advanceAfterRangeSearch, status,keyChange);
            //the label has been changed into the finally block.
        }
        return new KeyChangeStatus(status, keyChange);
  }


  /**
   * 
   * Retrieve the next or previous record.  Prevents phantoms.
   */
  // line 724 "../../../Cursor.ump"
  public OperationStatus retrieveNext(DatabaseEntry key, DatabaseEntry data, LockMode lockMode, GetMode getMode) throws DatabaseException{
    if (!isSerializableIsolation(lockMode)) {
            return retrieveNextAllowPhantoms(key, data, getLockType(lockMode, false), getMode);
        }
        while (true) {
            try {
                OperationStatus status;
                if (getMode == GetMode.NEXT_DUP) {
                    status = getNextDupAndRangeLock(key, data, lockMode);
                } else {
                    if (!getMode.isForward()) {
                        rangeLockCurrentPosition(getMode);
                    }
                    LockType lockType = getLockType(lockMode, getMode.isForward());
                    status = retrieveNextAllowPhantoms(key, data, lockType, getMode);
                    if (getMode.isForward() && status != OperationStatus.SUCCESS) {
                        cursorImpl.lockEofNode(LockType.RANGE_READ);
                    }
                }
                return status;
            } catch (RangeRestartException e) {
                continue;
            }
        }
  }


  /**
   * 
   * Retrieve the next dup; if no next dup is found then range lock the following key for phantom prevention.  Importantly, the cursor position is not changed if there are no more dups, even though we advance to the following key in order to range lock it.
   */
  // line 754 "../../../Cursor.ump"
   private OperationStatus getNextDupAndRangeLock(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) throws DatabaseException{
    DatabaseEntry tryKey = new DatabaseEntry();
        DatabaseEntry tryData = new DatabaseEntry();
        LockType lockType = getLockType(lockMode, true);
        OperationStatus status;
        boolean noNextKeyFound;
        while (true) {
            label26: //Label:           ;  //this.hook26();
                CursorImpl dup = beginRead(true);
            try {
                KeyChangeStatus result = dup.getNextWithKeyChangeStatus(tryKey, tryData, lockType, true, false);
                status = result.status;
                noNextKeyFound = (status != OperationStatus.SUCCESS);
                if (result.keyChange && status == OperationStatus.SUCCESS) {
                    status = OperationStatus.NOTFOUND;
                }
            } catch (DatabaseException DBE) {
                endRead(dup, false);
                throw DBE;
            }
            if (checkForInsertion(GetMode.NEXT, cursorImpl, dup)) {
                endRead(dup, false);
                continue;
            } else {
                endRead(dup, status == OperationStatus.SUCCESS);
                Label27: //Label:           ;  //this.hook27();
                    break;
            }
        }
        if (noNextKeyFound) {
            cursorImpl.lockEofNode(LockType.RANGE_READ);
        }
        if (status == OperationStatus.SUCCESS) {
            key.setData(tryKey.getData(), 0, tryKey.getSize());
            data.setData(tryData.getData(), 0, tryData.getSize());
        }
        return status;
  }


  /**
   * 
   * For 'prev' operations, upgrade to a range lock at the current position. For PREV_NODUP, range lock the first duplicate instead.  If there are no records at the current position, get a range lock on the next record or, if not found, on the logical EOF node.  Do not modify the current cursor position, use a separate cursor.
   */
  // line 796 "../../../Cursor.ump"
   private void rangeLockCurrentPosition(GetMode getMode) throws DatabaseException{
    DatabaseEntry tempKey = new DatabaseEntry();
        DatabaseEntry tempData = new DatabaseEntry();
        tempKey.setPartial(0, 0, true);
        tempData.setPartial(0, 0, true);
        OperationStatus status;
        CursorImpl dup = cursorImpl.cloneCursor(true);
        try {
            if (getMode == GetMode.PREV_NODUP) {
                status = dup.getFirstDuplicate(tempKey, tempData, LockType.RANGE_READ);
            } else {
                status = dup.getCurrent(tempKey, tempData, LockType.RANGE_READ);
            }
            if (status != OperationStatus.SUCCESS) {
                while (true) {
                    Label28:
assert LatchSupport.countLatchesHeld() == 0;
		//original();
 //Label:           ;  //this.hook28();
                        status = dup.getNext(tempKey, tempData, LockType.RANGE_READ, true, false);
                    if (checkForInsertion(GetMode.NEXT, cursorImpl, dup)) {
                        dup.close();
                        dup = cursorImpl.cloneCursor(true);
                        continue;
                    } else {
                        Label29:
assert LatchSupport.countLatchesHeld() == 0;
		//original();
 //Label:           ;  //this.hook29();
                            break;
                    }
                }
            }
        } finally {
            if (cursorImpl == dup) {
                dup.reset();
            } else {
                dup.close();
            }
        }
        if (status != OperationStatus.SUCCESS) {
            cursorImpl.lockEofNode(LockType.RANGE_READ);
        }
  }


  /**
   * 
   * Retrieve without preventing phantoms.
   */
  // line 839 "../../../Cursor.ump"
   private OperationStatus retrieveNextAllowPhantoms(DatabaseEntry key, DatabaseEntry data, LockType lockType, GetMode getMode) throws DatabaseException{
    assert(key != null && data != null);
        OperationStatus status;
        while (true) {
            Label30:
assert LatchSupport.countLatchesHeld() == 0;
	//original();
 //Label:           ;  //this.hook30();
                CursorImpl dup = beginRead(true);
            try {
                if (getMode == GetMode.NEXT) {
                    status = dup.getNext(key, data, lockType, true, false);
                } else if (getMode == GetMode.PREV) {
                    status = dup.getNext(key, data, lockType, false, false);
                } else if (getMode == GetMode.NEXT_DUP) {
                    status = dup.getNextDuplicate(key, data, lockType, true, false);
                } else if (getMode == GetMode.PREV_DUP) {
                    status = dup.getNextDuplicate(key, data, lockType, false, false);
                } else if (getMode == GetMode.NEXT_NODUP) {
                    status = dup.getNextNoDup(key, data, lockType, true, false);
                } else if (getMode == GetMode.PREV_NODUP) {
                    status = dup.getNextNoDup(key, data, lockType, false, false);
                } else {
                    throw new InternalException("unknown GetMode");
                }
            } catch (DatabaseException DBE) {
                endRead(dup, false);
                throw DBE;
            }
            if (checkForInsertion(getMode, cursorImpl, dup)) {
                endRead(dup, false);
                continue;
            } else {
                endRead(dup, status == OperationStatus.SUCCESS);
                Label31:
assert LatchSupport.countLatchesHeld() == 0;
	//original();
 //Label:           ;  //this.hook31();
                    break;
            }
        }
        return status;
  }


  /**
   * 
   * Returns the current key and data.  There is no need to prevent phantoms.
   */
  // line 881 "../../../Cursor.ump"
  public OperationStatus getCurrentInternal(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) throws DatabaseException{
    LockType lockType = getLockType(lockMode, false);
        return cursorImpl.getCurrent(key, data, lockType);
  }

  // line 887 "../../../Cursor.ump"
   private boolean checkForInsertion(GetMode getMode, CursorImpl origCursor, CursorImpl dupCursor) throws DatabaseException{
    BIN origBIN = origCursor.getBIN();
        BIN dupBIN = dupCursor.getBIN();
        DBIN origDBIN = origCursor.getDupBIN();
        boolean forward = true;
        if (getMode == GetMode.PREV || getMode == GetMode.PREV_DUP || getMode == GetMode.PREV_NODUP) {
            forward = false;
        }
        boolean ret = false;
        if (origBIN != dupBIN) {
            Label33:
origCursor.latchBINs();
	//original(origCursor);
 //Label:           ;  //this.hook33(origCursor);
            if (origDBIN == null) {
                if (forward) {
                    if (origBIN.getNEntries() - 1 > origCursor.getIndex()) {
                        for (int i = origCursor.getIndex() + 1; i < origBIN.getNEntries(); i++) {
                            if (!origBIN.isEntryKnownDeleted(i)) {
                                Node n = origBIN.fetchTarget(i);
                                if (n != null && !n.containsDuplicates()) {
                                    LN ln = (LN) n;
                                    if (!ln.isDeleted()) {
                                        ret = true;
                                        break;
                                    }
                                }
                            } else {}
                        }
                    }
                } else {
                    if (origCursor.getIndex() > 0) {
                        for (int i = 0; i < origCursor.getIndex(); i++) {
                            if (!origBIN.isEntryKnownDeleted(i)) {
                                Node n = origBIN.fetchTarget(i);
                                if (n != null && !n.containsDuplicates()) {
                                    LN ln = (LN) n;
                                    if (!ln.isDeleted()) {
                                        ret = true;
                                        break;
                                    }
                                } else {}
                            }
                        }
                    }
                }
            }
            Label32:
origCursor.releaseBINs();
	//original(origCursor);
 //Label:           ;  //this.hook32(origCursor);
                return ret;
        }
        if (origDBIN != dupCursor.getDupBIN() && origCursor.getIndex() == dupCursor.getIndex() &&
            getMode != GetMode.NEXT_NODUP && getMode != GetMode.PREV_NODUP) {
            Label35:
origCursor.latchBINs();
	//original(origCursor);
 //Label:           ;  //this.hook35(origCursor);
            if (forward) {
                if (origDBIN.getNEntries() - 1 > origCursor.getDupIndex()) {
                    for (int i = origCursor.getDupIndex() + 1; i < origDBIN.getNEntries(); i++) {
                        if (!origDBIN.isEntryKnownDeleted(i)) {
                            Node n = origDBIN.fetchTarget(i);
                            LN ln = (LN) n;
                            if (n != null && !ln.isDeleted()) {
                                ret = true;
                                break;
                            }
                        }
                    }
                }
            } else {
                if (origCursor.getDupIndex() > 0) {
                    for (int i = 0; i < origCursor.getDupIndex(); i++) {
                        if (!origDBIN.isEntryKnownDeleted(i)) {
                            Node n = origDBIN.fetchTarget(i);
                            LN ln = (LN) n;
                            if (n != null && !ln.isDeleted()) {
                                ret = true;
                                break;
                            }
                        }
                    }
                }
            }
            Label34:
origCursor.releaseBINs();
	//original(origCursor);
 //Label:           ;  //this.hook34(origCursor);
            return ret;
        }
        return false;
  }


  /**
   * 
   * If the cursor is initialized, dup it and return the dup; otherwise, return the original.  This avoids the overhead of duping when the original is uninitialized.  The cursor returned must be passed to endRead() to close the correct cursor.
   */
  // line 973 "../../../Cursor.ump"
   private CursorImpl beginRead(boolean addCursor) throws DatabaseException{
    CursorImpl dup;
        if (cursorImpl.isNotInitialized()) {
            dup = cursorImpl;
        } else {
            dup = cursorImpl.cloneCursor(addCursor);
        }
        return dup;
  }


  /**
   * 
   * If the operation is successful, swaps cursors and closes the original cursor; otherwise, closes the duped cursor.  In the case where the original cursor was not duped by beginRead because it was uninitialized, just resets the original cursor if the operation did not succeed.
   */
  // line 986 "../../../Cursor.ump"
   private void endRead(CursorImpl dup, boolean success) throws DatabaseException{
    if (dup == cursorImpl) {
            if (!success) {
                cursorImpl.reset();
            }
        } else {
            if (success) {
                cursorImpl.close();
                cursorImpl = dup;
            } else {
                dup.close();
            }
        }
  }

  // line 1001 "../../../Cursor.ump"
  public boolean advanceCursor(DatabaseEntry key, DatabaseEntry data){
    return cursorImpl.advanceCursor(key, data);
  }

  // line 1005 "../../../Cursor.ump"
   private LockType getLockType(LockMode lockMode, boolean rangeLock){
    if (isReadUncommittedMode(lockMode)) {
            return LockType.NONE;
        } else if (lockMode == null || lockMode == LockMode.DEFAULT) {
            return rangeLock ? LockType.RANGE_READ : LockType.READ;
        } else if (lockMode == LockMode.RMW) {
            return rangeLock ? LockType.RANGE_WRITE : LockType.WRITE;
        } else if (lockMode == LockMode.READ_COMMITTED) {
            throw new IllegalArgumentException(lockMode.toString() + " not allowed with Cursor methods");
        } else {
            assert false: lockMode;
            return LockType.NONE;
        }
  }


  /**
   * 
   * Returns whether the given lock mode will cause a read-uncommitted when used with this cursor, taking into account the default cursor configuration.
   */
  // line 1023 "../../../Cursor.ump"
  public boolean isReadUncommittedMode(LockMode lockMode){
    return (lockMode == LockMode.READ_UNCOMMITTED ||
            (readUncommittedDefault && (lockMode == null || lockMode == LockMode.DEFAULT)));
  }

  // line 1028 "../../../Cursor.ump"
   private boolean isSerializableIsolation(LockMode lockMode){
    return serializableIsolationDefault && !isReadUncommittedMode(lockMode);
  }

  // line 1032 "../../../Cursor.ump"
   protected void checkUpdatesAllowed(String operation) throws DatabaseException{
    if (updateOperationsProhibited) {
            throw new DatabaseException("A transaction was not supplied when opening this cursor: " + operation);
        }
  }


  /**
   * 
   * Note that this flavor of checkArgs doesn't require that the dbt data is set.
   */
  // line 1041 "../../../Cursor.ump"
   private void checkArgsNoValRequired(DatabaseEntry key, DatabaseEntry data){
    DatabaseUtil.checkForNullDbt(key, "key", false);
        DatabaseUtil.checkForNullDbt(data, "data", false);
  }


  /**
   * 
   * Note that this flavor of checkArgs requires that the dbt data is set.
   */
  // line 1049 "../../../Cursor.ump"
   private void checkArgsValRequired(DatabaseEntry key, DatabaseEntry data){
    DatabaseUtil.checkForNullDbt(key, "key", true);
        DatabaseUtil.checkForNullDbt(data, "data", true);
  }


  /**
   * 
   * Check the environment and cursor state.
   */
  // line 1057 "../../../Cursor.ump"
  public void checkState(boolean mustBeInitialized) throws DatabaseException{
    checkEnv();
        cursorImpl.checkCursorState(mustBeInitialized);
  }


  /**
   * 
   * @throws RunRecoveryException if the underlying environment is invalid.
   */
  // line 1065 "../../../Cursor.ump"
  public void checkEnv() throws RunRecoveryException{
    cursorImpl.checkEnv();
  }

  // line 1069 "../../../Cursor.ump"
   private void traceCursorImpl(StringBuffer sb){
    sb.append(" locker=").append(cursorImpl.getLocker().getId());
        if (cursorImpl.getBIN() != null) {
            sb.append(" bin=").append(cursorImpl.getBIN().getNodeId());
        }
        sb.append(" idx=").append(cursorImpl.getIndex());
        if (cursorImpl.getDupBIN() != null) {
            sb.append(" Dbin=").append(cursorImpl.getDupBIN().getNodeId());
        }
        sb.append(" dupIdx=").append(cursorImpl.getDupIndex());
  }


  /**
   * protected void hook0() throws DatabaseException {}
   * 
   * protected void hook1() throws DatabaseException {}
   * 
   * protected void hook2(DatabaseEntry key, DatabaseEntry data) throws DatabaseException {}
   * 
   * protected void hook3(DatabaseEntry key, DatabaseEntry data) throws DatabaseException {}
   * 
   * protected void hook4(DatabaseEntry key, DatabaseEntry data) throws DatabaseException {}
   * 
   * protected void hook5(DatabaseEntry data) throws DatabaseException {}
   * 
   * protected void hook6(LockMode lockMode) throws DatabaseException {}
   * 
   * protected void hook7(LockMode lockMode) throws DatabaseException {}
   * 
   * protected void hook8(LockMode lockMode) throws DatabaseException {}
   * 
   * protected void hook9(LockMode lockMode) throws DatabaseException {}
   * 
   * protected void hook10(LockMode lockMode) throws DatabaseException {}
   * 
   * protected void hook11(LockMode lockMode) throws DatabaseException {}
   * 
   * protected void hook12(LockMode lockMode) throws DatabaseException {}
   * 
   * protected void hook13(LockMode lockMode) throws DatabaseException {}
   * 
   * protected void hook14(LockMode lockMode) throws DatabaseException {}
   * 
   * protected void hook15(DatabaseEntry key, LockMode lockMode) throws DatabaseException {}
   * 
   * protected void hook16(DatabaseEntry key, LockMode lockMode) throws DatabaseException {}
   * 
   * protected void hook17(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) throws DatabaseException {}
   * 
   * protected void hook18(DatabaseEntry key, DatabaseEntry data, LockMode lockMode) throws DatabaseException {}
   * 
   * protected void hook19(CursorImpl dup) throws DatabaseException {}
   * 
   * protected void hook20(CursorImpl origCursor, CursorImpl dup) throws DatabaseException {}
   * 
   * protected void hook21(CursorImpl origCursor) throws DatabaseException {}
   * 
   * protected void hook22() throws DatabaseException {}
   * 
   * protected void hook23() throws DatabaseException {}
   * 
   * protected void hook24() throws DatabaseException {}
   */
  // line 1134 "../../../Cursor.ump"
   protected void hook25(CursorImpl dup, DatabaseEntry key, DatabaseEntry data, LockType searchLockType, LockType advanceLockType, SearchMode searchMode, boolean advanceAfterRangeSearch, OperationStatus status, boolean keyChange) throws DatabaseException{
    int searchResult = dup.searchAndPosition(key, data, searchMode, searchLockType);
        if ((searchResult & CursorImpl.FOUND) != 0) {
            boolean exactKeyMatch = ((searchResult & CursorImpl.EXACT_KEY) != 0);
            boolean exactDataMatch = ((searchResult & CursorImpl.EXACT_DATA) != 0);
            boolean foundLast = ((searchResult & CursorImpl.FOUND_LAST) != 0);
            boolean rangeMatch = false;
            if (searchMode == SearchMode.SET_RANGE && !exactKeyMatch) {
                rangeMatch = true;
            }
            if (searchMode == SearchMode.BOTH_RANGE && (!exactKeyMatch || !exactDataMatch)) {
                rangeMatch = true;
            }
            DatabaseEntry useKey = (searchMode == SearchMode.SET) ? null : key;
            if (rangeMatch || (status = dup.getCurrentAlreadyLatched(useKey, data, searchLockType,
                    true)) == OperationStatus.KEYEMPTY) {
                if (foundLast) {
                    status = OperationStatus.NOTFOUND;
                } else if (searchMode == SearchMode.SET) {
                    status = dup.getNextDuplicate(key, data, advanceLockType, true, rangeMatch);
                } else if (searchMode == SearchMode.BOTH) {
                    if (status == OperationStatus.KEYEMPTY) {
                        status = OperationStatus.NOTFOUND;
                    }
                } else {
                    assert!searchMode.isExactSearch();
                    byte[] searchKey = null;
                    if (searchMode.isDataSearch()) {
                        searchKey = Key.makeKey(key);
                    }
                    if (exactKeyMatch) {
                        KeyChangeStatus result = dup.getNextWithKeyChangeStatus(key, data, advanceLockType, true,
                            rangeMatch);
                        status = result.status;
                        keyChange = searchMode.isDataSearch() ? (status == OperationStatus.SUCCESS) : result.keyChange;
                    } else if (searchMode.isDataSearch() && !advanceAfterRangeSearch) {
                        status = OperationStatus.NOTFOUND;
                    } else {
                        status = dup.getNextNoDup(key, data, advanceLockType, true, rangeMatch);
                        keyChange = (status == OperationStatus.SUCCESS);
                    }
                    if (status == OperationStatus.SUCCESS && searchMode.isDataSearch()) {
                        if (Key.compareKeys(key.getData(), searchKey, dbImpl.getDuplicateComparator()) != 0) {
                            status = OperationStatus.NOTFOUND;
                        }
                    }
                }
            }
        }
  }


  /**
   * 
   * Send trace messages to the java.util.logger. Don't rely on the logger alone to conditionalize whether we send this message, we don't even want to construct the message if the level is not enabled.
   */
  // line 9 "../../../LoggingFinest_Cursor.ump"
  public void trace(Level level, String methodName, DatabaseEntry key, DatabaseEntry data, LockMode lockMode){
    new Cursor_trace(this, level, methodName, key, data, lockMode).execute();
  }


  /**
   * 
   * Send trace messages to the java.util.logger. Don't rely on the logger alone to conditionalize whether we send this message, we don't even want to construct the message if the level is not enabled.
   */
  // line 16 "../../../LoggingFinest_Cursor.ump"
  public void trace(Level level, String methodName, LockMode lockMode){
    new Cursor_trace2(this, level, methodName, lockMode).execute();
  }


  public String toString()
  {
    return super.toString() + "["+ "]" + System.getProperties().getProperty("line.separator") +
            "  " + "cursorImpl" + "=" + (getCursorImpl() != null ? !getCursorImpl().equals(this)  ? getCursorImpl().toString().replaceAll("  ","    ") : "this" : "null") + System.getProperties().getProperty("line.separator") +
            "  " + "config" + "=" + (getConfig() != null ? !getConfig().equals(this)  ? getConfig().toString().replaceAll("  ","    ") : "this" : "null");
  }  /*PLEASE DO NOT EDIT THIS CODE*/
  /*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/
  
  
  
  // line 4 "../../../LoggingFinest_Cursor_inner.ump"
  // line 24 "../../../Derivative_LoggingFinest_LoggingBase_Cursor_inner.ump"
  public static class Cursor_trace2
  {
  
    //------------------------
    // MEMBER VARIABLES
    //------------------------
  
    //------------------------
    // CONSTRUCTOR
    //------------------------
  
    public Cursor_trace2()
    {}
  
    //------------------------
    // INTERFACE
    //------------------------
  
    public void delete()
    {}
  
    // line 6 "../../../LoggingFinest_Cursor_inner.ump"
    public  Cursor_trace2(Cursor _this, Level level, String methodName, LockMode lockMode){
      this._this=_this;
          this.level=level;
          this.methodName=methodName;
          this.lockMode=lockMode;
    }
  
    // line 12 "../../../LoggingFinest_Cursor_inner.ump"
    public void execute(){
      // line 26 "../../../Derivative_LoggingFinest_LoggingBase_Cursor_inner.ump"
      if (_this.logger.isLoggable(level)) {
                sb=new StringBuffer();
                sb.append(methodName);
                _this.traceCursorImpl(sb);
                if (lockMode != null) {
                  sb.append(" lockMode=").append(lockMode);
                }
                _this.logger.log(level,sb.toString());
              }
             // original();
      // END OF UMPLE BEFORE INJECTION
      
    }
    
    //------------------------
    // DEVELOPER CODE - PROVIDED AS-IS
    //------------------------
    
    // line 13 "../../../LoggingFinest_Cursor_inner.ump"
    protected Cursor _this ;
  // line 14 "../../../LoggingFinest_Cursor_inner.ump"
    protected Level level ;
  // line 15 "../../../LoggingFinest_Cursor_inner.ump"
    protected String methodName ;
  // line 16 "../../../LoggingFinest_Cursor_inner.ump"
    protected LockMode lockMode ;
  // line 17 "../../../LoggingFinest_Cursor_inner.ump"
    protected StringBuffer sb ;
  
    
  }  /*PLEASE DO NOT EDIT THIS CODE*/
  /*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/
  
  
  
  // line 19 "../../../LoggingFinest_Cursor_inner.ump"
  // line 4 "../../../Derivative_LoggingFinest_LoggingBase_Cursor_inner.ump"
  public static class Cursor_trace
  {
  
    //------------------------
    // MEMBER VARIABLES
    //------------------------
  
    //------------------------
    // CONSTRUCTOR
    //------------------------
  
    public Cursor_trace()
    {}
  
    //------------------------
    // INTERFACE
    //------------------------
  
    public void delete()
    {}
  
    // line 21 "../../../LoggingFinest_Cursor_inner.ump"
    public  Cursor_trace(Cursor _this, Level level, String methodName, DatabaseEntry key, DatabaseEntry data, LockMode lockMode){
      this._this=_this;
          this.level=level;
          this.methodName=methodName;
          this.key=key;
          this.data=data;
          this.lockMode=lockMode;
    }
  
    // line 29 "../../../LoggingFinest_Cursor_inner.ump"
    public void execute(){
      // line 6 "../../../Derivative_LoggingFinest_LoggingBase_Cursor_inner.ump"
      if (_this.logger.isLoggable(level)) {
                sb=new StringBuffer();
                sb.append(methodName);
                _this.traceCursorImpl(sb);
                if (key != null) {
                  sb.append(" key=").append(key.dumpData());
                }
                if (data != null) {
                  sb.append(" data=").append(data.dumpData());
                }
                if (lockMode != null) {
                  sb.append(" lockMode=").append(lockMode);
                }
                _this.logger.log(level,sb.toString());
              }
             // original();
      // END OF UMPLE BEFORE INJECTION
      
    }
    
    //------------------------
    // DEVELOPER CODE - PROVIDED AS-IS
    //------------------------
    
    // line 30 "../../../LoggingFinest_Cursor_inner.ump"
    protected Cursor _this ;
  // line 31 "../../../LoggingFinest_Cursor_inner.ump"
    protected Level level ;
  // line 32 "../../../LoggingFinest_Cursor_inner.ump"
    protected String methodName ;
  // line 33 "../../../LoggingFinest_Cursor_inner.ump"
    protected DatabaseEntry key ;
  // line 34 "../../../LoggingFinest_Cursor_inner.ump"
    protected DatabaseEntry data ;
  // line 35 "../../../LoggingFinest_Cursor_inner.ump"
    protected LockMode lockMode ;
  // line 36 "../../../LoggingFinest_Cursor_inner.ump"
    protected StringBuffer sb ;
  
    
  }  
  //------------------------
  // DEVELOPER CODE - PROVIDED AS-IS
  //------------------------
  
  // line 37 "../../../Cursor.ump"
  private boolean updateOperationsProhibited ;
// line 42 "../../../Cursor.ump"
  private Database dbHandle ;
// line 47 "../../../Cursor.ump"
  private DatabaseImpl dbImpl ;
// line 49 "../../../Cursor.ump"
  private boolean readUncommittedDefault ;
// line 51 "../../../Cursor.ump"
  private boolean serializableIsolationDefault ;
// line 5 "../../../loggingBase_Cursor.ump"
  private Logger logger ;

  
}