/*PLEASE DO NOT EDIT THIS CODE*/
/*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/

package com.sleepycat.je.dbi;
import de.ovgu.cide.jakutil.*;
import com.sleepycat.je.utilint.TestHookExecute;
import com.sleepycat.je.utilint.TestHook;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.txn.ThreadLocker;
import com.sleepycat.je.txn.Locker;
import com.sleepycat.je.txn.LockType;
import com.sleepycat.je.txn.LockResult;
import com.sleepycat.je.txn.LockGrantType;
import com.sleepycat.je.txn.BasicLocker;
import com.sleepycat.je.tree.TreeWalkerStatsAccumulator;
import com.sleepycat.je.tree.Tree;
import com.sleepycat.je.tree.Node;
import com.sleepycat.je.tree.LN;
import com.sleepycat.je.tree.Key;
import com.sleepycat.je.tree.IN;
import com.sleepycat.je.tree.DupCountLN;
import com.sleepycat.je.tree.DIN;
import com.sleepycat.je.tree.DBIN;
import com.sleepycat.je.tree.BINBoundary;
import com.sleepycat.je.tree.BIN;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.RunRecoveryException;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DatabaseEntry;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Comparator;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.latch.LatchNotHeldException;
import com.sleepycat.je.log.entry.*;

// line 3 "../../../../CursorImpl.ump"
// line 3 "../../../../CursorImpl_static.ump"
// line 3 "../../../../Latches_CursorImpl.ump"
// line 4 "../../../../Latches_CursorImpl_inner.ump"
// line 3 "../../../../MemoryBudget_CursorImpl.ump"
// line 3 "../../../../Evictor_CursorImpl.ump"
// line 3 "../../../../INCompressor_CursorImpl.ump"
// line 3 "../../../../INCompressor_CursorImpl_inner.ump"
// line 3 "../../../../Verifier_CursorImpl.ump"
// line 3 "../../../../Verifier_CursorImpl_inner.ump"
// line 3 "../../../../Statistics_CursorImpl.ump"
// line 3 "../../../../Statistics_CursorImpl_inner.ump"
// line 3 "../../../../Derivative_Evictor_CriticalEviction_CursorImpl.ump"
// line 3 "../../../../Derivative_Latches_Evictor_CursorImpl.ump"
// line 3 "../../../../Derivative_Latches_Statistics_CursorImpl.ump"
// line 3 "../../../../Derivative_Latches_Statistics_CursorImpl_inner.ump"
public class CursorImpl implements Cloneable
{

  //------------------------
  // MEMBER VARIABLES
  //------------------------

  //------------------------
  // CONSTRUCTOR
  //------------------------

  public CursorImpl()
  {}

  //------------------------
  // INTERFACE
  //------------------------
  // the method 'delete()' has been disabled.  

  // line 101 "../../../../CursorImpl.ump"
   private static  long getNextCursorId(){
    return ++lastAllocatedId;
  }

  // line 105 "../../../../CursorImpl.ump"
   public int hashCode(){
    return thisId;
  }

  // line 109 "../../../../CursorImpl.ump"
   private TreeWalkerStatsAccumulator getTreeStatsAccumulator(){
    if (EnvironmentImpl.getThreadLocalReferenceCount() > 0) {
            return (TreeWalkerStatsAccumulator) treeStatsAccumulatorTL.get();
        } else {
            return null;
        }
  }

  // line 117 "../../../../CursorImpl.ump"
   public void incrementLNCount(){
    TreeWalkerStatsAccumulator treeStatsAccumulator = getTreeStatsAccumulator();
        if (treeStatsAccumulator != null) {
            treeStatsAccumulator.incrementLNCount();
        }
  }

  // line 124 "../../../../CursorImpl.ump"
   public void setNonCloning(boolean nonCloning){
    this.nonCloning = nonCloning;
  }


  /**
   * 
   * Creates a cursor with retainNonTxnLocks=true.
   */
  // line 131 "../../../../CursorImpl.ump"
   public  CursorImpl(DatabaseImpl database, Locker locker) throws DatabaseException{
    this(database, locker, true);
  }


  /**
   * 
   * Creates a cursor.
   * @param retainNonTxnLocksis true if non-transactional locks should be retained (notreleased automatically) when the cursor is closed.
   */
  // line 139 "../../../../CursorImpl.ump"
   public  CursorImpl(DatabaseImpl database, Locker locker, boolean retainNonTxnLocks) throws DatabaseException{
    thisId = (int) getNextCursorId();
        bin = null;
        index = -1;
        dupBin = null;
        dupIndex = -1;
        assert!(retainNonTxnLocks && (locker instanceof ThreadLocker));
        assert!(!retainNonTxnLocks && locker.getClass() == BasicLocker.class);
        this.retainNonTxnLocks = retainNonTxnLocks;
        this.database = database;
        this.locker = locker;
        this.locker.registerCursor(this);
        status = CURSOR_NOT_INITIALIZED;
  }


  /**
   * 
   * Shallow copy. addCursor() is optionally called.
   */
  // line 157 "../../../../CursorImpl.ump"
   public CursorImpl cloneCursor(boolean addCursor) throws DatabaseException{
    return cloneCursor(addCursor, null);
  }


  /**
   * 
   * Shallow copy. addCursor() is optionally called. Allows inheriting the BIN position from some other cursor.
   */
  // line 164 "../../../../CursorImpl.ump"
   public CursorImpl cloneCursor(boolean addCursor, CursorImpl usePosition) throws DatabaseException{
    CursorImpl ret = null;
        if (nonCloning) {
            ret = this;
        } else {
            try {
                Label206:
latchBINs();
 
                    ret = (CursorImpl) super.clone();
                if (!retainNonTxnLocks) {
                    ret.locker = locker.newNonTxnLocker();
                }
                ret.locker.registerCursor(ret);
                if (usePosition != null && usePosition.status == CURSOR_INITIALIZED) {
                    ret.bin = usePosition.bin;
                    ret.index = usePosition.index;
                    ret.dupBin = usePosition.dupBin;
                    ret.dupIndex = usePosition.dupIndex;
                }
                if (addCursor) {
                    ret.addCursor();
                }
            }
            catch (CloneNotSupportedException cannotOccur) {
                return null;
            } finally {
                Label207:
releaseBINs();
 ;
            }
        }
				Label_return:
if (allowEviction) {

				 Label203:
// Label203 introduced in Evictor_CursorImpl.ump

	database.getDbEnvironment().getEvictor().doCriticalEviction();
//	original();
	; 
			}

        return ret;
  }

  // line 196 "../../../../CursorImpl.ump"
   public int getIndex(){
    return index;
  }

  // line 200 "../../../../CursorImpl.ump"
   public void setIndex(int idx){
    index = idx;
  }

  // line 204 "../../../../CursorImpl.ump"
   public BIN getBIN(){
    return bin;
  }

  // line 208 "../../../../CursorImpl.ump"
   public void setBIN(BIN newBin){
    bin = newBin;
  }

  // line 212 "../../../../CursorImpl.ump"
   public BIN getBINToBeRemoved(){
    return binToBeRemoved;
  }

  // line 216 "../../../../CursorImpl.ump"
   public int getDupIndex(){
    return dupIndex;
  }

  // line 220 "../../../../CursorImpl.ump"
   public void setDupIndex(int dupIdx){
    dupIndex = dupIdx;
  }

  // line 224 "../../../../CursorImpl.ump"
   public DBIN getDupBIN(){
    return dupBin;
  }

  // line 228 "../../../../CursorImpl.ump"
   public void setDupBIN(DBIN newDupBin){
    dupBin = newDupBin;
  }

  // line 232 "../../../../CursorImpl.ump"
   public DBIN getDupBINToBeRemoved(){
    return dupBinToBeRemoved;
  }

  // line 236 "../../../../CursorImpl.ump"
   public void setTreeStatsAccumulator(TreeWalkerStatsAccumulator tSA){
    treeStatsAccumulatorTL.set(tSA);
  }


  /**
   * 
   * Figure out which BIN/index set to use.
   */
  // line 243 "../../../../CursorImpl.ump"
   private boolean setTargetBin(){
    targetBin = null;
        targetIndex = 0;
        boolean isDup = (dupBin != null);
        dupKey = null;
        if (isDup) {
            targetBin = dupBin;
            targetIndex = dupIndex;
            dupKey = dupBin.getDupKey();
        } else {
            targetBin = bin;
            targetIndex = index;
        }
        return isDup;
  }


  /**
   * 
   * Advance a cursor. Used so that verify can advance a cursor even in the face of an exception [12932].
   * @param keyon return contains the key if available, or null.
   * @param dataon return contains the data if available, or null.
   */
  // line 264 "../../../../CursorImpl.ump"
   public boolean advanceCursor(DatabaseEntry key, DatabaseEntry data){
    BIN oldBin = bin;
        BIN oldDupBin = dupBin;
        int oldIndex = index;
        int oldDupIndex = dupIndex;
        key.setData(null);
        data.setData(null);
        try {
            getNext(key, data, LockType.NONE, true, false);
        } catch (DatabaseException ignored) {}
        if (bin != oldBin || dupBin != oldDupBin || index != oldIndex || dupIndex != oldDupIndex) {
            if (key.getData() == null && bin != null && index > 0) {
                setDbt(key, bin.getKey(index));
            }
            if (data.getData() == null && dupBin != null && dupIndex > 0) {
                setDbt(data, dupBin.getKey(dupIndex));
            }
            return true;
        } else {
            return false;
        }
  }

  // line 287 "../../../../CursorImpl.ump"
   public BIN latchBIN() throws DatabaseException{
    Label244:
while (bin != null) {
						BIN waitingOn = bin;
						waitingOn.latch();
						if (bin == waitingOn) {
							return bin;
						}
						waitingOn.releaseLatch();
					}
 

          return null;
  }

  // line 295 "../../../../CursorImpl.ump"
   public DBIN latchDBIN() throws DatabaseException{
    Label246:
while (dupBin != null) {
						BIN waitingOn = dupBin;
						waitingOn.latch();
						if (dupBin == waitingOn) {
							return dupBin;
						}
						waitingOn.releaseLatch();
						}
 ;
		return dupBin;
  }

  // line 301 "../../../../CursorImpl.ump"
   public Locker getLocker(){
    return locker;
  }

  // line 305 "../../../../CursorImpl.ump"
   public void addCursor(BIN bin){
    if (bin != null) {
            Label208:
assert bin.isLatchOwner();
 //this.hook208(bin);
                bin.addCursor(this);
        }
  }


  /**
   * 
   * Add to the current cursor. (For dups)
   */
  // line 315 "../../../../CursorImpl.ump"
   public void addCursor(){
    if (dupBin != null) {
            addCursor(dupBin);
        }
        if (bin != null) {
            addCursor(bin);
        }
  }

  // line 324 "../../../../CursorImpl.ump"
   public void updateBin(BIN bin, int index) throws DatabaseException{
    removeCursorDBIN();
        setDupIndex(-1);
        setDupBIN(null);
        setIndex(index);
        setBIN(bin);
        addCursor(bin);
  }

  // line 333 "../../../../CursorImpl.ump"
   public void updateDBin(DBIN dupBin, int dupIndex){
    setDupIndex(dupIndex);
        setDupBIN(dupBin);
        addCursor(dupBin);
  }

  // line 339 "../../../../CursorImpl.ump"
   private void removeCursor() throws DatabaseException{
    removeCursorBIN();
        removeCursorDBIN();
  }

  // line 344 "../../../../CursorImpl.ump"
   private void removeCursorBIN() throws DatabaseException{
    BIN abin = latchBIN();
        if (abin != null) {
            abin.removeCursor(this);
            Label209:
abin.releaseLatch();
        //original(abin);
 ; //this.hook209(abin);
        }
  }

  // line 352 "../../../../CursorImpl.ump"
   private void removeCursorDBIN() throws DatabaseException{
    DBIN abin = latchDBIN();
        if (abin != null) {
            abin.removeCursor(this);
            Label210:
abin.releaseLatch();
        //original(abin);
 ;//this.hook210(abin);
        }
  }


  /**
   * 
   * Clear the reference to the dup tree, if any.
   */
  // line 363 "../../../../CursorImpl.ump"
   public void clearDupBIN(boolean alreadyLatched) throws DatabaseException{
    if (dupBin != null) {
            if (alreadyLatched) {
                dupBin.removeCursor(this);
                Label211:
dupBin.releaseLatch();
        //original();
 ;//this.hook211();
            } else {
                removeCursorDBIN();
            }
            dupBin = null;
            dupIndex = -1;
        }
  }

  // line 376 "../../../../CursorImpl.ump"
   public void dumpTree() throws DatabaseException{
    database.getTree().dump();
  }


  /**
   * 
   * @return true if this cursor is closed
   */
  // line 383 "../../../../CursorImpl.ump"
   public boolean isClosed(){
    return (status == CURSOR_CLOSED);
  }


  /**
   * 
   * @return true if this cursor is not initialized
   */
  // line 390 "../../../../CursorImpl.ump"
   public boolean isNotInitialized(){
    return (status == CURSOR_NOT_INITIALIZED);
  }


  /**
   * 
   * Reset a cursor to an uninitialized state, but unlike close(), allow it to be used further.
   */
  // line 397 "../../../../CursorImpl.ump"
   public void reset() throws DatabaseException{
    removeCursor();
        if (!retainNonTxnLocks) {
            locker.releaseNonTxnLocks();
        }
        bin = null;
        index = -1;
        dupBin = null;
        dupIndex = -1;
        status = CURSOR_NOT_INITIALIZED;
    // line 16 "../../../../Derivative_Evictor_CriticalEviction_CursorImpl.ump"
    if (allowEviction) {
    	    database.getDbEnvironment().getEvictor().doCriticalEviction();
    	}
    // END OF UMPLE AFTER INJECTION
  }


  /**
   * 
   * Close a cursor.
   * @throws DatabaseExceptionif the cursor was previously closed.
   */
  // line 413 "../../../../CursorImpl.ump"
   public void close() throws DatabaseException{
    assert assertCursorState(false): dumpToString(true);
        removeCursor();
        locker.unRegisterCursor(this);
        if (!retainNonTxnLocks) {
            locker.releaseNonTxnLocks();
        }
        status = CURSOR_CLOSED;
    // line 27 "../../../../Derivative_Evictor_CriticalEviction_CursorImpl.ump"
    if (allowEviction) {
    	    database.getDbEnvironment().getEvictor().doCriticalEviction();
    	}
    // END OF UMPLE AFTER INJECTION
  }

  // line 423 "../../../../CursorImpl.ump"
   public int count(LockType lockType) throws DatabaseException{
    try {
            assert assertCursorState(true): dumpToString(true);
            if (!database.getSortedDuplicates()) {
                return 1;
            }
            if (bin == null) {
                return 0;
            }
            
latchBIN();
Label212: 
			 			if (bin.getNEntries() <= index) {
				        return 0;
				    }
				    Node n = bin.fetchTarget(index);
				    if (n != null && n.containsDuplicates()) {
				        DIN dupRoot = (DIN) n;
				        Label265:
dupRoot.latch();
    releaseBIN();
 ;
				        DupCountLN dupCountLN = (DupCountLN) dupRoot.getDupCountLNRef().fetchTarget(database, dupRoot);
				        Label264:
dupRoot.releaseLatch();
 ;
				        if (lockType != LockType.NONE) {
				            locker.lock(dupCountLN.getNodeId(), lockType, false, database);
				        }
                return dupCountLN.getDupCount();

				    } else {
				        				        return 1;
				    }

        } finally
				{
				Label265_1:
releaseBIN();
 ;
				}
  }


  /**
   * 
   * Delete the item pointed to by the cursor. If cursor is not initialized or item is already deleted, return appropriate codes. Returns with nothing latched. bin and dupBin are latched as appropriate.
   * @return 0 on success, appropriate error code otherwise.
   */
  // line 461 "../../../../CursorImpl.ump"
   public OperationStatus delete() throws DatabaseException{
    return delete(true);
  }

  // line 465 "../../../../CursorImpl.ump"
   public OperationStatus delete(boolean b) throws DatabaseException{
    assert assertCursorState(true): dumpToString(true);
        boolean isDup = setTargetBin();
        if (targetBin == null) {
            return OperationStatus.KEYEMPTY;
        }
        if (targetBin.isEntryKnownDeleted(targetIndex)) {
            Label214:
releaseBINs();
 ; 
            return OperationStatus.KEYEMPTY;
        }
        LN ln = (LN) targetBin.fetchTarget(targetIndex);
        if (ln == null) {
            Label215:
releaseBINs();
 ;//this.hook215();
            return OperationStatus.KEYEMPTY;
        }
        LockResult lockResult = lockLN(ln, LockType.WRITE);
        ln = lockResult.getLN();
        if (ln == null) {
            Label216:
releaseBINs();
 ;//this.hook216();
            return OperationStatus.KEYEMPTY;
        }
        LockResult dclLockResult = null;
        DIN dupRoot = null;
        Label213:      ;   //this.hook213(isDup, ln, lockResult, dclLockResult, dupRoot);
				try {
            isDup = (dupBin != null);
        if (isDup) {
            dupRoot = getLatchedDupRoot(true);
            dclLockResult = lockDupCountLN(dupRoot, LockType.WRITE);
            dupRoot = (DIN) bin.getTarget(index);
            Label267:
releaseBIN();
 ;//this.hook267();
        }
        setTargetBin();
        long oldLsn = targetBin.getLsn(targetIndex);
        byte[] lnKey = targetBin.getKey(targetIndex);
        lockResult.setAbortLsn(oldLsn, targetBin.isEntryKnownDeleted(targetIndex));
        long oldLNSize = 0;
        //	oldLNSize = this.hook284(ln, oldLNSize);
        Label284:
oldLNSize = ln.getMemorySizeIncludedByParent();
;
        long newLsn = ln.delete(database, lnKey, dupKey, oldLsn, locker);
        long newLNSize = 0;
        //	newLNSize = this.hook283(ln, newLNSize);
        Label283:
newLNSize = ln.getMemorySizeIncludedByParent();

            targetBin.updateEntry(targetIndex, newLsn, oldLNSize, newLNSize);
        targetBin.setPendingDeleted(targetIndex);
        Label266:
releaseBINs();
 //this.hook266();

        if (isDup) {
            dupRoot.incrementDuplicateCount(dclLockResult, dupKey, locker, false);
            Label268:
dupRoot.releaseLatch();
 ;//this.hook268(dupRoot);
            dupRoot = null;
            Label281: ;//this.hook281(lnKey);
        } else {
            Label282: ;//this.hook282(lnKey);
        }
        Label204: ;//this.hook204(ln, oldLsn, newLsn);
				}
				finally 
				{
					Label213_1:
if (dupRoot != null) {
                dupRoot.releaseLatchIfOwner();
            }
 ;
				}
        //end of hook213
        return OperationStatus.SUCCESS;
  }


  /**
   * 
   * Return a new copy of the cursor. If position is true, position the returned cursor at the same position.
   */
  // line 533 "../../../../CursorImpl.ump"
   public CursorImpl dup(boolean samePosition) throws DatabaseException{
    assert assertCursorState(false): dumpToString(true);
        CursorImpl ret = cloneCursor(samePosition);
        if (!samePosition) {
            ret.bin = null;
            ret.index = -1;
            ret.dupBin = null;
            ret.dupIndex = -1;
            ret.status = CURSOR_NOT_INITIALIZED;
        }
        return ret;
  }


  /**
   * 
   * Search for the next key (or duplicate) following the given key (and datum), and acquire a range insert lock on it. If there are no more records following the given key and datum, lock the special EOF node for the database.
   */
  // line 549 "../../../../CursorImpl.ump"
   public void lockNextKeyForInsert(DatabaseEntry key, DatabaseEntry data) throws DatabaseException{
    DatabaseEntry tempKey = new DatabaseEntry
            (key.getData(), key.getOffset(), key.getSize());
        DatabaseEntry tempData = new DatabaseEntry
            (data.getData(), data.getOffset(), data.getSize());
        tempKey.setPartial(0, 0, true);
        tempData.setPartial(0, 0, true);
        boolean lockedNextKey = false;

        /* Don't search for data if duplicates are not configured. */
        SearchMode searchMode = database.getSortedDuplicates() ?
            SearchMode.BOTH_RANGE : SearchMode.SET_RANGE;
        boolean latched = true;
Label248:
latched = true;
            try {
                
 ;

            /* Search. */
            int searchResult = searchAndPosition
                (tempKey, tempData, searchMode, LockType.RANGE_INSERT);
            if ((searchResult & FOUND) != 0 &&
                (searchResult & FOUND_LAST) == 0) {

                /*
                 * If searchAndPosition found a record (other than the last
                 * one), in all cases we should advance to the next record:
                 *
                 * 1- found a deleted record,
                 * 2- found an exact match, or
                 * 3- found the record prior to the given key/data.
                 *
                 * If we didn't match the key, skip over duplicates to the next
                 * key with getNextNoDup.
                 */
                OperationStatus status;
                if ((searchResult & EXACT_KEY) != 0) {
                    status = getNext
                        (tempKey, tempData, LockType.RANGE_INSERT, true, true);
                } else {
                    status = getNextNoDup
                        (tempKey, tempData, LockType.RANGE_INSERT, true, true);
                }
                if (status == OperationStatus.SUCCESS) {
                    lockedNextKey = true;
                }
                Label249:
latched = false;
 ;

            }
						

            }
             finally {
            if (latched) {
                releaseBINs();
            }
        		}
Label248_1:;
      

        /* Lock the EOF node if no next key was found. */
        if (!lockedNextKey) {
            lockEofNode(LockType.RANGE_INSERT);
        }
  }


  /**
   * 
   * Insert the given LN in the tree or return KEYEXIST if the key is already present. <p> This method is called directly internally for putting tree map LNs and file summary LNs. It should not be used otherwise, and in the future we should find a way to remove this special case. </p>
   */
  // line 608 "../../../../CursorImpl.ump"
   public OperationStatus putLN(byte [] key, LN ln, boolean allowDuplicates) throws DatabaseException{
    assert assertCursorState(false): dumpToString(true);
        Label217:
assert LatchSupport.countLatchesHeld() == 0;
        //original();
 ;//this.hook217();
        LockResult lockResult = locker.lock(ln.getNodeId(), LockType.WRITE, false, database);
        if (database.getTree().insert(ln, key, allowDuplicates, this, lockResult)) {
            status = CURSOR_INITIALIZED;
            return OperationStatus.SUCCESS;
        } else {
            locker.releaseLock(ln.getNodeId());
            return OperationStatus.KEYEXIST;
        }
  }


  /**
   * 
   * Insert or overwrite the key/data pair.
   * @param key
   * @param data
   * @return 0 if successful, failure status value otherwise
   */
  // line 628 "../../../../CursorImpl.ump"
   public OperationStatus put(DatabaseEntry key, DatabaseEntry data, DatabaseEntry foundData) throws DatabaseException{
    assert assertCursorState(false): dumpToString(true);
        OperationStatus result = putLN(Key.makeKey(key), new LN(data), database.getSortedDuplicates());
        if (result == OperationStatus.KEYEXIST) {
            status = CURSOR_INITIALIZED;
            result = putCurrent(data, null, foundData);
        }
        return result;
  }


  /**
   * 
   * Insert the key/data pair in No Overwrite mode.
   * @param key
   * @param data
   * @return 0 if successful, failure status value otherwise
   */
  // line 644 "../../../../CursorImpl.ump"
   public OperationStatus putNoOverwrite(DatabaseEntry key, DatabaseEntry data) throws DatabaseException{
    assert assertCursorState(false): dumpToString(true);
        return putLN(Key.makeKey(key), new LN(data), false);
  }


  /**
   * 
   * Insert the key/data pair as long as no entry for key/data exists yet.
   */
  // line 652 "../../../../CursorImpl.ump"
   public OperationStatus putNoDupData(DatabaseEntry key, DatabaseEntry data) throws DatabaseException{
    assert assertCursorState(false): dumpToString(true);
        if (!database.getSortedDuplicates()) {
            throw new DatabaseException(
                "putNoDupData() called, but database is not configured " + "for duplicate data.");
        }
        return putLN(Key.makeKey(key), new LN(data), true);
  }


  /**
   * 
   * Modify the current record with this data.
   * @param data
   */
  // line 666 "../../../../CursorImpl.ump"
   public OperationStatus putCurrent(DatabaseEntry data, DatabaseEntry foundKey, DatabaseEntry foundData) throws DatabaseException{
    try {
            assert assertCursorState(true): dumpToString(true);
            if (foundKey != null) {
                foundKey.setData(null);
            }
            if (foundData != null) {
                foundData.setData(null);
            }
            if (bin == null) {
                return OperationStatus.KEYEMPTY;
            }
            Label219:
latchBINs();
        //original();
 ;//this.hook219();
            boolean isDup = setTargetBin();
						Label218: ;//            this.hook218(data, foundKey, foundData, isDup);
	 					LN ln = (LN) targetBin.fetchTarget(targetIndex);
				    byte[] lnKey = targetBin.getKey(targetIndex);
				    Comparator userComparisonFcn = targetBin.getKeyComparator();
				    if (targetBin.isEntryKnownDeleted(targetIndex) || ln == null) {
				        Label270:
releaseBINs();
    //original();
 ;//this.hook270();
				        return OperationStatus.NOTFOUND;
				    }
				    LockResult lockResult = lockLN(ln, LockType.WRITE);
				    ln = lockResult.getLN();
				    if (ln == null) {
				        Label271:
releaseBINs();
    //original();
 ;//.hook271();
				        return OperationStatus.NOTFOUND;
				    }
				    byte[] foundDataBytes;
				    byte[] foundKeyBytes;
				    isDup = setTargetBin();
				    if (isDup) {
				        foundDataBytes = lnKey;
				        foundKeyBytes = targetBin.getDupKey();
				    } else {
				        foundDataBytes = ln.getData();
				        foundKeyBytes = lnKey;
				    }
				    byte[] newData;
				    if (data.getPartial()) {
				        int dlen = data.getPartialLength();
				        int doff = data.getPartialOffset();
				        int origlen = (foundDataBytes != null) ? foundDataBytes.length : 0;
				        int oldlen = (doff + dlen > origlen) ? doff + dlen : origlen;
				        int len = oldlen - dlen + data.getSize();
				        if (len == 0) {
				            newData = LogUtils.ZERO_LENGTH_BYTE_ARRAY;
				        } else {
				            newData = new byte[len];
				        }
				        int pos = 0;
				        int slicelen = (doff < origlen) ? doff : origlen;
				        if (slicelen > 0)
				            System.arraycopy(foundDataBytes, 0, newData, pos, slicelen);
				        pos += doff;
				        slicelen = data.getSize();
				        System.arraycopy(data.getData(), data.getOffset(), newData, pos, slicelen);
				        pos += slicelen;
				        slicelen = origlen - (doff + dlen);
				        if (slicelen > 0)
				            System.arraycopy(foundDataBytes, doff + dlen, newData, pos, slicelen);
				    } else {
				        int len = data.getSize();
				        if (len == 0) {
				            newData = LogUtils.ZERO_LENGTH_BYTE_ARRAY;
				        } else {
				            newData = new byte[len];
				        }
				        System.arraycopy(data.getData(), data.getOffset(), newData, 0, len);
				    }
				    if (database.getSortedDuplicates()) {
				        boolean keysEqual = false;
				        if (foundDataBytes != null) {
				            keysEqual = Key.compareKeys(foundDataBytes, newData, userComparisonFcn) == 0;
				        }
				        if (!keysEqual) {
				            revertLock(ln, lockResult);
				            throw new DatabaseException("Can't replace a duplicate with different data.");
				        }
				    }
				    if (foundData != null) {
				        setDbt(foundData, foundDataBytes);
				    }
				    if (foundKey != null) {
				        setDbt(foundKey, foundKeyBytes);
				    }
				    long oldLsn = targetBin.getLsn(targetIndex);
				    lockResult.setAbortLsn(oldLsn, targetBin.isEntryKnownDeleted(targetIndex));
				    long oldLNSize = 0;
				    //	oldLNSize = this.hook286(ln, oldLNSize);
				    Lable286: ; 
				    byte[] newKey = (isDup ? targetBin.getDupKey() : lnKey);
				    long newLsn = ln.modify(newData, database, newKey, oldLsn, locker);
				    long newLNSize = 0;
				    //newLNSize = this.hook285(ln, newLNSize);
				    Label285:
				        targetBin.updateEntry(targetIndex, newLsn, oldLNSize, newLNSize);
				    Label269:
releaseBINs();
    //original();
 ;//this.hook269();
				    Label205: ;//this.hook205(ln, oldLsn, newLsn);
				    status = CURSOR_INITIALIZED;
				                return OperationStatus.SUCCESS;
						}
						finally{            
											Label218_1:
//    try {
            //original(data, foundKey, foundData, isDup);
      //  } finally {
            releaseBINs();
        //}
 ;
		      
        }
  }


  /**
   * 
   * Retrieve the current record.
   */
  // line 778 "../../../../CursorImpl.ump"
   public OperationStatus getCurrent(DatabaseEntry foundKey, DatabaseEntry foundData, LockType lockType) throws DatabaseException{
    assert assertCursorState(true): dumpToString(true);
        if (bin == null) {
            return OperationStatus.KEYEMPTY;
        }
        Label220:
if (dupBin == null) {
            latchBIN();
        } else {
            latchDBIN();
        }
        //original();
 //this.hook220();
        return getCurrentAlreadyLatched(foundKey, foundData, lockType, true);
  }


  /**
   * 
   * Retrieve the current record. Assume the bin is already latched. Return with the target bin unlatched.
   */
  // line 792 "../../../../CursorImpl.ump"
   public OperationStatus getCurrentAlreadyLatched(DatabaseEntry foundKey, DatabaseEntry foundData, LockType lockType, boolean first) throws DatabaseException{
    assert assertCursorState(true): dumpToString(true);
				try {
              return fetchCurrent(foundKey, foundData, lockType, first);

        } 
				finally {
             Label221:
releaseBINs();
 ;
        }
  }


  /**
   * 
   * Retrieve the current LN, return with the target bin unlatched.
   */
  // line 807 "../../../../CursorImpl.ump"
   public LN getCurrentLN(LockType lockType) throws DatabaseException{
    assert assertCursorState(true): dumpToString(true);
        if (bin == null) {
            return null;
        } else {
            Label222:
latchBIN();
        //original();
 
            return getCurrentLNAlreadyLatched(lockType);
        }
  }


  /**
   * 
   * Retrieve the current LN, assuming the BIN is already latched. Return with the target BIN unlatched.
   */
  // line 820 "../../../../CursorImpl.ump"
   public LN getCurrentLNAlreadyLatched(LockType lockType) throws DatabaseException{
    try {
            Label223: 
    				assert assertCursorState(true): dumpToString(true);
				    Label272:
assert checkAlreadyLatched(true): dumpToString(true);
    //original();
 
				    if (bin == null) {
				        return null;
				    }
				    LN ln = null;
				    if (!bin.isEntryKnownDeleted(index)) {
				        ln = (LN) bin.fetchTarget(index);
				    }
				    if (ln == null) {
				        Label273:
releaseBIN();
    //original();

				        return null;
				    }
				    addCursor(bin);
				    LockResult lockResult = lockLN(ln, lockType);
				    ln = lockResult.getLN();
				    return ln;
						}
						finally{
						Label223_1:
// try {
            //original(lockType);
        //} finally {
            releaseBINs();
        //}
 ;
												
										}
  }

  // line 848 "../../../../CursorImpl.ump"
   public OperationStatus getNext(DatabaseEntry foundKey, DatabaseEntry foundData, LockType lockType, boolean forward, boolean alreadyLatched) throws DatabaseException{
    return getNextWithKeyChangeStatus(foundKey, foundData, lockType, forward, alreadyLatched).status;
  }


  /**
   * 
   * Move the cursor forward and return the next record. This will cross BIN boundaries and dip into duplicate sets.
   * @param foundKeyDatabaseEntry to use for returning key
   * @param foundDataDatabaseEntry to use for returning data
   * @param forwardif true, move forward, else move backwards
   * @param alreadyLatchedif true, the bin that we're on is already latched.
   * @return the status and an indication of whether we advanced to a new keyduring the operation.
   */
  // line 861 "../../../../CursorImpl.ump"
   public KeyChangeStatus getNextWithKeyChangeStatus(DatabaseEntry foundKey, DatabaseEntry foundData, LockType lockType, boolean forward, boolean alreadyLatched) throws DatabaseException{
    assert assertCursorState(true): dumpToString(true);
        Label224:
assert checkAlreadyLatched(alreadyLatched): dumpToString(true);
        //original(alreadyLatched);
 ; //this.hook224(alreadyLatched);
        KeyChangeStatus result = new KeyChangeStatus(OperationStatus.NOTFOUND, true);
        try {
            while (bin != null) {
                if (dupBin != null) {
                    Label277:
if (DEBUG) {
					verifyCursor(dupBin);
			}
//			original();
 //this.hook277();
                        if (getNextDuplicate(foundKey, foundData, lockType, forward,
                                alreadyLatched) == OperationStatus.SUCCESS) {
                            result.status = OperationStatus.SUCCESS;
                            result.keyChange = false;
                            break;
                        } else {
                            removeCursorDBIN();
                            Label226:
alreadyLatched = false;
        //return //original(alreadyLatched);
 ;//alreadyLatched = this.hook226(alreadyLatched);
                            dupBin = null;
                            dupIndex = -1;
                            continue;
                        }
                }
                Label225:
assert checkAlreadyLatched(alreadyLatched): dumpToString(true);
        if (!alreadyLatched) {
            latchBIN();
        } else {
            alreadyLatched = false;
        }
        //return //original(alreadyLatched);
 //alreadyLatched = this.hook225(alreadyLatched);
                Label276:
if (DEBUG) {
			  verifyCursor(bin);
		}
		//original();
 //this.hook276();
                    if ((forward && ++index < bin.getNEntries()) || (!forward && --index > -1)) {
                        OperationStatus ret = getCurrentAlreadyLatched(foundKey, foundData, lockType, forward);
                        if (ret == OperationStatus.SUCCESS) {
                            incrementLNCount();
                            result.status = OperationStatus.SUCCESS;
                            break;
                        } else {
                            Label227:
assert LatchSupport.countLatchesHeld() == 0;
        //original();
 //this.hook227();
                            if (binToBeRemoved != null) {
                                flushBINToBeRemoved();
                            }
                            continue;
                        }
                    } else {
                        if (binToBeRemoved != null) {
                            Label229:
releaseBIN();
        //original();
 ;//this.hook229();
                            flushBINToBeRemoved();
                            Label228:
latchBIN();
        //original();
 ;//this.hook228();
                        }
                        binToBeRemoved = bin;
                        bin = null;
                        BIN newBin;
                        assert TestHookExecute.doHookIfSet(testHook);
                        if (forward) {
                            newBin = database.getTree().getNextBin(binToBeRemoved, false);
                        } else {
                            newBin = database.getTree().getPrevBin(binToBeRemoved, false);
                        }
                        if (newBin == null) {
                            result.status = OperationStatus.NOTFOUND;
                            break;
                        } else {
                            if (forward) {
                                index = -1;
                            } else {
                                index = newBin.getNEntries();
                            }
                            addCursor(newBin);
                            bin = newBin;
                            Label230:
alreadyLatched = true;
        //original(alreadyLatched);
 ;//this.hook230(alreadyLatched);
                        }
                    }
            }
        } finally {
            Label231:
assert LatchSupport.countLatchesHeld() == 0: LatchSupport.latchesHeldToString();
        //original();
 //this.hook231();
            if (binToBeRemoved != null) {
                flushBINToBeRemoved();
            }
        }
        return result;
  }

  // line 936 "../../../../CursorImpl.ump"
   private void flushBINToBeRemoved() throws DatabaseException{
    // line 215 "../../../../Latches_CursorImpl.ump"
    binToBeRemoved.latch();
            //original();
    // END OF UMPLE BEFORE INJECTION
    binToBeRemoved.removeCursor(this);
        Label232:
binToBeRemoved.releaseLatch();
        //original();
 ;//this.hook232();
        binToBeRemoved = null;
  }

  // line 943 "../../../../CursorImpl.ump"
   public OperationStatus getNextNoDup(DatabaseEntry foundKey, DatabaseEntry foundData, LockType lockType, boolean forward, boolean alreadyLatched) throws DatabaseException{
    assert assertCursorState(true): dumpToString(true);
        if (dupBin != null) {
            clearDupBIN(alreadyLatched);
            alreadyLatched = false;
        }
        return getNext(foundKey, foundData, lockType, forward, alreadyLatched);
  }


  /**
   * 
   * Retrieve the first duplicate at the current cursor position.
   */
  // line 956 "../../../../CursorImpl.ump"
   public OperationStatus getFirstDuplicate(DatabaseEntry foundKey, DatabaseEntry foundData, LockType lockType) throws DatabaseException{
    assert assertCursorState(true): dumpToString(true);
        if (dupBin != null) {
            removeCursorDBIN();
            dupBin = null;
            dupIndex = -1;
        }
        return getCurrent(foundKey, foundData, lockType);
  }


  /**
   * 
   * Enter with dupBin unlatched. Pass foundKey == null to just advance cursor to next duplicate without fetching data.
   */
  // line 970 "../../../../CursorImpl.ump"
   public OperationStatus getNextDuplicate(DatabaseEntry foundKey, DatabaseEntry foundData, LockType lockType, boolean forward, boolean alreadyLatched) throws DatabaseException{
    assert assertCursorState(true) : dumpToString(true);
					Label250:
assert checkAlreadyLatched(alreadyLatched) : dumpToString(true);
 ;
					try {
							while (dupBin != null) {
					Label251:
if (!alreadyLatched) {
												latchDBIN();
										} else {
												alreadyLatched = false;
										}
 ;

				Label279:
if (DEBUG) {
								verifyCursor(dupBin);
						}
 ;

						/* Are we still on this DBIN? */
						if ((forward && ++dupIndex < dupBin.getNEntries()) ||
								(!forward && --dupIndex > -1)) {

								OperationStatus ret = OperationStatus.SUCCESS;
								if (foundKey != null) {
							ret = getCurrentAlreadyLatched(foundKey, foundData,
								                                               lockType, forward);
								} else {
									Label252:
releaseDBIN();
 ;
								}
								if (ret == OperationStatus.SUCCESS) {
							incrementLNCount();
							return ret;
								} else {
							Label257:
assert LatchSupport.countLatchesHeld() == 0;
 ;

							if (dupBinToBeRemoved != null) {
									flushDBINToBeRemoved();
							}

							continue;
								}

						} else {

								/*
								 * We need to go to the next DBIN.  Remove the cursor and
								 * be sure to change the dupBin field after removing the
								 * cursor.
								 */
								if (dupBinToBeRemoved != null) {
							flushDBINToBeRemoved();
								}
								dupBinToBeRemoved = dupBin;

								dupBin = null;
								Label255:
dupBinToBeRemoved.releaseLatch();
 ;
								        
								Label275:
;
     TreeWalkerStatsAccumulator treeStatsAccumulator =
								                getTreeStatsAccumulator();
								            if (treeStatsAccumulator != null) {
								                Label200:
latchBIN();
 ; //latchBIN();
								                try {
								                    if (index < 0) {
								                        /* This duplicate tree has been deleted. */
								                        return OperationStatus.NOTFOUND;
								                    }

								                    DIN duplicateRoot = (DIN) bin.fetchTarget(index);
								                     Label201:
duplicateRoot.latch();
 ; //duplicateRoot.latch();
								                    try {
								                        DupCountLN dcl = duplicateRoot.getDupCountLN();
								                        if (dcl != null) {
								                            dcl.accumulateStats(treeStatsAccumulator);
								                        }
								                    } finally {
								                        Label201_1:
duplicateRoot.releaseLatch();
 ;
								                    }
								                } finally {
								                    Label200_1:
releaseBIN();
 ; //releaseBIN();
								                }
								            }
 ;
								
								Label254:
assert (LatchSupport.countLatchesHeld() == 0);
								dupBinToBeRemoved.latch();
 ;

								DBIN newDupBin;

								if (forward) {
							newDupBin = (DBIN) database.getTree().getNextBin
									(dupBinToBeRemoved, 	
							 	     true /* traverseWithinDupTree*/);
								} else {
							newDupBin = (DBIN) database.getTree().getPrevBin
									(dupBinToBeRemoved,
									 true /* traverseWithinDupTree*/);
								}

								if (newDupBin == null) {
							return OperationStatus.NOTFOUND;
								} else {
							if (forward) {
									dupIndex = -1;
							} else {
									dupIndex = newDupBin.getNEntries();
							}
							addCursor(newDupBin);

							/* 
							 * Ensure that setting dupBin is under newDupBin's
							 * latch.
							 */
							dupBin = newDupBin;
							Label256:
alreadyLatched = true;
 ;
								}
						}
							}
					} finally {
							Label253:
assert LatchSupport.countLatchesHeld() == 0;
 ;
							if (dupBinToBeRemoved != null) {
						flushDBINToBeRemoved();
							}
					}

								return OperationStatus.NOTFOUND;
  }

  // line 1063 "../../../../CursorImpl.ump"
   private void flushDBINToBeRemoved() throws DatabaseException{
    // line 226 "../../../../Latches_CursorImpl.ump"
    dupBinToBeRemoved.latch();
            //original();
    // END OF UMPLE BEFORE INJECTION
    dupBinToBeRemoved.removeCursor(this);
        Label233:
dupBinToBeRemoved.releaseLatch();
        //original();
 //this.hook233();
        dupBinToBeRemoved = null;
  }


  /**
   * 
   * Position the cursor at the first or last record of the database. It's okay if this record is deleted. Returns with the target BIN latched.
   * @return true if a first or last position is found, false if the treebeing searched is empty.
   */
  // line 1074 "../../../../CursorImpl.ump"
   public boolean positionFirstOrLast(boolean first, DIN duplicateRoot) throws DatabaseException{
    assert assertCursorState(false): dumpToString(true);
            IN in = null;
            boolean found = false;        

            Label234:
try {
            
 //this.hook234(first, duplicateRoot, in , found);
            if (duplicateRoot == null) {
            removeCursorBIN();
            if (first) { in = database.getTree().getFirstNode();
            } else { in = database.getTree().getLastNode();
            }
            if ( in != null) {
                assert( in instanceof BIN);
                dupBin = null;
                dupIndex = -1;
                bin = (BIN) in ;
                index = (first ? 0 : (bin.getNEntries() - 1));
                addCursor(bin);
                TreeWalkerStatsAccumulator treeStatsAccumulator = getTreeStatsAccumulator();
                if (bin.getNEntries() == 0) {
                    found = true;
                } else {
                    Node n = null;
                    if (! in .isEntryKnownDeleted(index)) {
                        n = in .fetchTarget(index);
                    }
                    if (n != null && n.containsDuplicates()) {
                        DIN dupRoot = (DIN) n;
                        Label274:
dupRoot.latch(); in .releaseLatch();
    //original(in, dupRoot);
 //this.hook274( in , dupRoot); in = null;
                        found = positionFirstOrLast(first, dupRoot);
                    } else {
                        if (treeStatsAccumulator != null) {
                            if (n == null || ((LN) n).isDeleted()) {
                                treeStatsAccumulator.incrementDeletedLNCount();
                            } else {
                                treeStatsAccumulator.incrementLNCount();
                            }
                        }
                        found = true;
                    }
                }
            }
        } else {
            removeCursorDBIN();
            if (first) { in = database.getTree().getFirstNode(duplicateRoot);
            } else { in = database.getTree().getLastNode(duplicateRoot);
            }
            if ( in != null) {
                assert( in instanceof DBIN);
                dupBin = (DBIN) in ;
                dupIndex = (first ? 0 : (dupBin.getNEntries() - 1));
                addCursor(dupBin);
                found = true;
            }
        }
        status = CURSOR_INITIALIZED;


  
        } 
       catch (DatabaseException e) {
            if ( in != null) 
            { in .releaseLatch();
            }
            throw e;
        }
Label234_1: ;
        return found;
//End of hook234
  }


  /**
   * 
   * Position the cursor at the key. This returns a three part value that's bitwise or'ed into the int. We find out if there was any kind of match and if the match was exact. Note that this match focuses on whether the searching criteria (key, or key and data, depending on the search type) is met. <p> Note this returns with the BIN latched! </p> <p> If this method returns without the FOUND bit set, the caller can assume that no match is possible. Otherwise, if the FOUND bit is set, the caller should check the EXACT_KEY and EXACT_DATA bits. If EXACT_KEY is not set (or for BOTH and BOTH_RANGE, if EXACT_DATA is not set), an approximate match was found. In an approximate match, the cursor is always positioned before the target key/data. This allows the caller to perform a 'next' operation to advance to the value that is equal or higher than the target key/data. </p> <p> Even if the search returns an exact result, the record may be deleted. The caller must therefore check for both an approximate match and for whether the cursor is positioned on a deleted record. </p> <p> If SET or BOTH is specified, the FOUND bit will only be returned if an exact match is found. However, the record found may be deleted. </p> <p> There is one special case where this method may be called without checking the EXACT_KEY (and EXACT_DATA) bits and without checking for a deleted record: If SearchMode.SET is specified then only the FOUND bit need be checked. When SET is specified and FOUND is returned, it is guaranteed to be an exact match on a non-deleted record. It is for this case only that this method is public. </p> <p> If FOUND is set, FOUND_LAST may also be set if the cursor is positioned on the last record in the database. Note that this state can only be counted on as long as the BIN is latched, so it is not set if this method must release the latch to lock the record. Therefore, it should only be used for optimizations. If FOUND_LAST is set, the cursor is positioned on the last record and the BIN is latched. If FOUND_LAST is not set, the cursor may or may not be positioned on the last record. Note that exact searches always perform an unlatch and a lock, so FOUND_LAST will only be set for inexact (range) searches. </p> <p> Be aware that when an approximate match is returned, the index or dupIndex may be set to -1. This is done intentionally so that a 'next' operation will increment it. </p>
   */
  // line 1143 "../../../../CursorImpl.ump"
   public int searchAndPosition(DatabaseEntry matchKey, DatabaseEntry matchData, SearchMode searchMode, LockType lockType) throws DatabaseException{
    boolean foundSomething = false;
    boolean foundExactKey = false;
    boolean foundExactData = false;
    boolean foundLast = false;
    boolean exactSearch = searchMode.isExactSearch();
    BINBoundary binBoundary = new BINBoundary();
    assert assertCursorState(false) : dumpToString(true);
    removeCursor();
    bin = null;
    try {
      Label235: ;
      byte[] key = Key.makeKey(matchKey);
      bin = (BIN) database.getTree().search(key, Tree.SearchType.NORMAL, -1, binBoundary, true);
      if (bin != null) {
        addCursor(bin);
        index = bin.findEntry(key, true, exactSearch);
        foundSomething = !exactSearch;
        dupBin = null;
        dupIndex = -1;
        boolean containsDuplicates = false;
        if (index >= 0) {
          if ((index & IN.EXACT_MATCH) != 0) {
            foundExactKey = true;
            index &= ~IN.EXACT_MATCH;
          }
          Node n = null;
          if (!bin.isEntryKnownDeleted(index)) {
            n = bin.fetchTarget(index);
          }
          if (n != null) {
            containsDuplicates = n.containsDuplicates();
            if (searchMode.isDataSearch()) {
              if (foundExactKey) {
                int searchResult = searchAndPositionBoth(containsDuplicates, n, matchData, exactSearch, lockType,
                    bin.getLsn(index));
                foundSomething = (searchResult & FOUND) != 0;
                foundExactData = (searchResult & EXACT_DATA) != 0;
              }
            } else {
              foundSomething = true;
              if (!containsDuplicates && exactSearch) {
                LN ln = (LN) n;
                LockResult lockResult = lockLN(ln, lockType);
                ln = lockResult.getLN();
                if (ln == null) {
                  foundSomething = false;
                }
              }
            }
          }
          foundLast = (searchMode == SearchMode.SET_RANGE && foundSomething && !containsDuplicates
              && binBoundary.isLastBin && index == bin.getNEntries() - 1);
        }
      }
      status = CURSOR_INITIALIZED;


      return ((foundSomething ? FOUND : 0) | (foundExactKey ? EXACT_KEY : 0) | (foundExactData ? EXACT_DATA : 0)
          | (foundLast ? FOUND_LAST : 0));

    } catch (DatabaseException e) {
      Label235_1:
releaseBIN();
 ;// extra catch statement will be addd.
      throw e;
    }
  }


  /**
   * 
   * For this type of search, we need to match both key and data. This method is called after the key is matched to perform the data portion of the match. We may be matching just against an LN, or doing further searching into the dup tree. See searchAndPosition for more details.
   */
  // line 1214 "../../../../CursorImpl.ump"
   private int searchAndPositionBoth(boolean containsDuplicates, Node n, DatabaseEntry matchData, boolean exactSearch, LockType lockType, long oldLsn) throws DatabaseException{
    assert assertCursorState(false): dumpToString(true);
        boolean found = false;
        boolean exact = false;
        assert(matchData != null);
        byte[] data = Key.makeKey(matchData);
        if (containsDuplicates) {
            DIN duplicateRoot = (DIN) n;
            Label236:
duplicateRoot.latch();
    releaseBIN();
    //original(duplicateRoot);
 //this.hook236(duplicateRoot);
            dupBin = (DBIN) database.getTree().searchSubTree(duplicateRoot, data, Tree.SearchType.NORMAL, -1, null,
                true);
            if (dupBin != null) {
                addCursor(dupBin);
                dupIndex = dupBin.findEntry(data, true, exactSearch);
                if (dupIndex >= 0) {
                    if ((dupIndex & IN.EXACT_MATCH) != 0) {
                        exact = true;
                    }
                    dupIndex &= ~IN.EXACT_MATCH;
                    found = true;
                } else {
                    dupIndex = -1;
                    found = !exactSearch;
                }
            }
        } else {
            LN ln = (LN) n;
            LockResult lockResult = lockLN(ln, lockType);
            ln = lockResult.getLN();
            if (ln == null) {
                found = !exactSearch;
            } else {
                dupBin = null;
                dupIndex = -1;
                int cmp = Key.compareKeys(ln.getData(), data, database.getDuplicateComparator());
                if (cmp == 0 || (cmp <= 0 && !exactSearch)) {
                    if (cmp == 0) {
                        exact = true;
                    }
                    found = true;
                } else {
                    index--;
                    found = !exactSearch;
                }
            }
        }
        return (found ? FOUND : 0) | (exact ? EXACT_DATA : 0);
  }

  // line 1265 "../../../../CursorImpl.ump"
   private OperationStatus fetchCurrent(DatabaseEntry foundKey, DatabaseEntry foundData, LockType lockType, boolean first) throws DatabaseException{
    TreeWalkerStatsAccumulator treeStatsAccumulator =
						getTreeStatsAccumulator();

						  boolean duplicateFetch = setTargetBin();
						  if (targetBin == null) {
						      return OperationStatus.NOTFOUND;
						  }

						Label259:
assert targetBin.isLatchOwner();
 ;

						  /* 
				 * Check the deleted flag in the BIN and make sure this isn't an empty
				 * BIN.  The BIN could be empty by virtue of the compressor running the
				 * size of this BIN to 0 but not having yet deleted it from the tree.
						   *
						   * The index may be negative if we're at an intermediate stage in an
						   * higher level operation, and we expect a higher level method to do a
						   * next or prev operation after this returns KEYEMPTY. [#11700]
				 */
						  Node n = null;

						  if (targetIndex < 0 ||
						      targetIndex >= targetBin.getNEntries() ||
						targetBin.isEntryKnownDeleted(targetIndex)) {
						      /* Node is no longer present. */
						  } else {

						/* 
						 * If we encounter a pendingDeleted entry, add it to the compressor
						 * queue.
						 */
						if (targetBin.isEntryPendingDeleted(targetIndex)) {
					EnvironmentImpl envImpl = database.getDbEnvironment();
					envImpl.addToCompressorQueue
							(targetBin, new Key(targetBin.getKey(targetIndex)), false);
						}

						      /* If fetchTarget returns null, a deleted LN was cleaned. */
						try {
					n = targetBin.fetchTarget(targetIndex);
						} catch (DatabaseException DE) {
					Label260:
targetBin.releaseLatchIfOwner();
 ;
					throw DE;
						}
						  }
						  if (n == null) {
						if (treeStatsAccumulator != null) {
					treeStatsAccumulator.incrementDeletedLNCount();
						}
								Label261:
targetBin.releaseLatchIfOwner();
 ;
						      return OperationStatus.KEYEMPTY;
						  }

						  /*
						   * Note that since we have the BIN/DBIN latched, we can safely check
						   * the node type. Any conversions from an LN to a dup tree must have
						   * the bin latched.
						   */
						  addCursor(targetBin);
						  if (n.containsDuplicates()) {
						      assert !duplicateFetch;
						      /* Descend down duplicate tree, doing latch coupling. */
						      DIN duplicateRoot = (DIN) n;
						      Label262:
duplicateRoot.latch();
						    targetBin.releaseLatch();
 ;

						      if (positionFirstOrLast(first, duplicateRoot)) {
					try {
							return fetchCurrent(foundKey, foundData, lockType, first);
					} catch (DatabaseException DE) {
							Label258:
releaseBINs();
 ; 
							throw DE;
					}
						      } else {
						          return OperationStatus.NOTFOUND;
						      }
						  }

						  LN ln = (LN) n;

				assert TestHookExecute.doHookIfSet(testHook);

						  /*
						   * Lock the LN.  For dirty-read, the data of the LN can be set to null
						   * at any time.  Cache the data in a local variable so its state does
						   * not change before calling setDbt further below.
						   */
				LockResult lockResult = lockLN(ln, lockType);
				try {
						      ln = lockResult.getLN();
						      byte[] lnData = (ln != null) ? ln.getData() : null;
						      if (ln == null || lnData == null) {
						          if (treeStatsAccumulator != null) {
						              treeStatsAccumulator.incrementDeletedLNCount();
						          }
						          return OperationStatus.KEYEMPTY;
						      }

						duplicateFetch = setTargetBin();
						      
						      /*
						       * Don't set the abort LSN here since we are not logging yet, even
						       * if this is a write lock.  Tree.insert depends on the fact that
						       * the abortLSN is not already set for deleted items.
						       */
						      if (duplicateFetch) {
						          if (foundData != null) {
						              setDbt(foundData, targetBin.getKey(targetIndex));
						          }
						          if (foundKey != null) {
						              setDbt(foundKey, targetBin.getDupKey());
						          }
						      } else {
						          if (foundData != null) {
						              setDbt(foundData, lnData);
						          }
						          if (foundKey != null) {
						              setDbt(foundKey, targetBin.getKey(targetIndex));
						          }
						      }

						      return OperationStatus.SUCCESS;
				} finally {
						Label263:
releaseBINs();
 ;

				}
  }


  /**
   * return new CursorImpl_fetchCurrent(this, foundKey, foundData, lockType, first).execute();
   * 
   * Locks the given LN's node ID; a deleted LN will not be locked or returned. Attempts to use a non-blocking lock to avoid unlatching/relatching. Retries if necessary, to handle the case where the LN is changed while the BIN is unlatched. Preconditions: The target BIN must be latched. When positioned in a dup tree, the BIN may be latched on entry also and if so it will be latched on exit. Postconditions: The target BIN is latched. When positioned in a dup tree, the BIN will be latched if it was latched on entry or a blocking lock was needed. Therefore, when positioned in a dup tree, releaseDBIN should be called.
   * @param lnthe LN to be locked.
   * @param lockTypethe type of lock requested.
   * @return the LockResult containing the LN that was locked, or containing anull LN if the LN was deleted or cleaned. If the LN is deleted, a lock will not be held.
   */
  // line 1401 "../../../../CursorImpl.ump"
   private LockResult lockLN(LN ln, LockType lockType) throws DatabaseException{
    LockResult lockResult = lockLNDeletedAllowed(ln, lockType);
        ln = lockResult.getLN();
        if (ln != null) {
            setTargetBin();
            if (targetBin.isEntryKnownDeleted(targetIndex) || ln.isDeleted()) {
                revertLock(ln.getNodeId(), lockResult.getLockGrant());
                lockResult.setLN(null);
            }
        }
        return lockResult;
  }


  /**
   * 
   * Locks the given LN's node ID; a deleted LN will be locked and returned. Attempts to use a non-blocking lock to avoid unlatching/relatching. Retries if necessary, to handle the case where the LN is changed while the BIN is unlatched. Preconditions: The target BIN must be latched. When positioned in a dup tree, the BIN may be latched on entry also and if so it will be latched on exit. Postconditions: The target BIN is latched. When positioned in a dup tree, the BIN will be latched if it was latched on entry or a blocking lock was needed. Therefore, when positioned in a dup tree, releaseDBIN should be called.
   * @param lnthe LN to be locked.
   * @param lockTypethe type of lock requested.
   * @return the LockResult containing the LN that was locked, or containing anull LN if the LN was cleaned.
   */
  // line 1420 "../../../../CursorImpl.ump"
   public LockResult lockLNDeletedAllowed(LN ln, LockType lockType) throws DatabaseException{
    LockResult lockResult;
        if (lockType == LockType.NONE) {
            lockResult = new LockResult(LockGrantType.NONE_NEEDED, null);
            lockResult.setLN(ln);
            return lockResult;
        }
        if (locker.getDefaultNoWait()) {
            lockResult = locker.lock(ln.getNodeId(), lockType, true, database);
        } else {
            lockResult = locker.nonBlockingLock(ln.getNodeId(), lockType, database);
        }
        if (lockResult.getLockGrant() != LockGrantType.DENIED) {
            lockResult.setLN(ln);
            return lockResult;
        }
        while (true) {
            long nodeId = ln.getNodeId();
            Label238:
releaseBINs();
    //original();
 //this.hook238();
            lockResult = locker.lock(nodeId, lockType, false, database);
            Label237:
latchBINs();
    //original();
 //this.hook237();
            setTargetBin();
            ln = (LN) targetBin.fetchTarget(targetIndex);
            if (ln != null && nodeId != ln.getNodeId()) {
                revertLock(nodeId, lockResult.getLockGrant());
                continue;
            } else {
                lockResult.setLN(ln);
                return lockResult;
            }
        }
  }


  /**
   * 
   * Locks the DupCountLN for the given duplicate root. Attempts to use a non-blocking lock to avoid unlatching/relatching. Preconditions: The dupRoot, BIN and DBIN are latched. Postconditions: The dupRoot, BIN and DBIN are latched. Note that the dupRoot may change during locking and should be refetched if needed.
   * @param dupRootthe duplicate root containing the DupCountLN to be locked.
   * @param lockTypethe type of lock requested.
   * @return the LockResult containing the LN that was locked.
   */
  // line 1459 "../../../../CursorImpl.ump"
   public LockResult lockDupCountLN(DIN dupRoot, LockType lockType) throws DatabaseException{
    DupCountLN ln = dupRoot.getDupCountLN();
        LockResult lockResult;
        if (locker.getDefaultNoWait()) {
            lockResult = locker.lock(ln.getNodeId(), lockType, true, database);
        } else {
            lockResult = locker.nonBlockingLock(ln.getNodeId(), lockType, database);
        }
        if (lockResult.getLockGrant() == LockGrantType.DENIED) {
            Label241:
dupRoot.releaseLatch();
    releaseBINs();
    //original(dupRoot);
 //this.hook241(dupRoot);
            lockResult = locker.lock(ln.getNodeId(), lockType, false, database);
            Label240:
latchBIN();
    //original();
 //this.hook240();
            dupRoot = (DIN) bin.fetchTarget(index);
            Label239:
dupRoot.latch();
    latchDBIN();
    //original(dupRoot);
 //this.hook239(dupRoot);
            ln = dupRoot.getDupCountLN();
        }
        lockResult.setLN(ln);
        return lockResult;
  }


  /**
   * 
   * Fetch, latch and return the DIN root of the duplicate tree at the cursor position. Preconditions: The BIN must be latched and the current BIN entry must contain a DIN. Postconditions: The BIN and DIN will be latched. The DBIN will remain latched if isDBINLatched is true.
   * @param isDBINLatchedis true if the DBIN is currently latched.
   */
  // line 1483 "../../../../CursorImpl.ump"
   public DIN getLatchedDupRoot(boolean isDBINLatched) throws DatabaseException{
    assert bin != null;
        Label243:
assert bin.isLatchOwner();
    //original();
 //this.hook243();
        assert index >= 0;
        DIN dupRoot = (DIN) bin.fetchTarget(index);
        Label242:
if (isDBINLatched) {
        if (!dupRoot.latchNoWait()) {
            releaseDBIN();
            dupRoot.latch();
            latchDBIN();
        }
    } else {
        dupRoot.latch();
    }
    //original(isDBINLatched, dupRoot);
 //this.hook242(isDBINLatched, dupRoot);
        return dupRoot;
  }


  /**
   * 
   * Helper to return a Data DBT from a BIN.
   */
  // line 1495 "../../../../CursorImpl.ump"
   private void setDbt(DatabaseEntry data, byte [] bytes){
    if (bytes != null) {
            boolean partial = data.getPartial();
            int off = partial ? data.getPartialOffset() : 0;
            int len = partial ? data.getPartialLength() : bytes.length;
            if (off + len > bytes.length) {
                len = (off > bytes.length) ? 0 : bytes.length - off;
            }
            byte[] newdata = null;
            if (len == 0) {
                newdata = LogUtils.ZERO_LENGTH_BYTE_ARRAY;
            } else {
                newdata = new byte[len];
                System.arraycopy(bytes, off, newdata, 0, len);
            }
            data.setData(newdata);
            data.setOffset(0);
            data.setSize(len);
        } else {
            data.setData(null);
            data.setOffset(0);
            data.setSize(0);
        }
  }


  /**
   * 
   * Calls checkCursorState and returns false is an exception is thrown.
   */
  // line 1523 "../../../../CursorImpl.ump"
   private boolean assertCursorState(boolean mustBeInitialized){
    try {
            checkCursorState(mustBeInitialized);
            return true;
        } catch (DatabaseException e) {
            return false;
        }
  }


  /**
   * 
   * Check that the cursor is open and optionally if it is initialized.
   */
  // line 1535 "../../../../CursorImpl.ump"
   public void checkCursorState(boolean mustBeInitialized) throws DatabaseException{
    if (status == CURSOR_INITIALIZED) {
            Label278:
if (DEBUG) {
					if (bin != null) {
				verifyCursor(bin);
					}
					if (dupBin != null) {
				verifyCursor(dupBin);
					}
			}
			//original();
 //this.hook278();
                return;
        }
        else if (status == CURSOR_NOT_INITIALIZED) {
            if (mustBeInitialized) {
                throw new DatabaseException("Cursor Not Initialized.");
            }
        } else if (status == CURSOR_CLOSED) {
            throw new DatabaseException("Cursor has been closed.");
        } else {
            throw new DatabaseException("Unknown cursor status: " + status);
        }
  }


  /**
   * 
   * Return this lock to its prior status. If the lock was just obtained, release it. If it was promoted, demote it.
   */
  // line 1554 "../../../../CursorImpl.ump"
   private void revertLock(LN ln, LockResult lockResult) throws DatabaseException{
    revertLock(ln.getNodeId(), lockResult.getLockGrant());
  }


  /**
   * 
   * Return this lock to its prior status. If the lock was just obtained, release it. If it was promoted, demote it.
   */
  // line 1561 "../../../../CursorImpl.ump"
   private void revertLock(long nodeId, LockGrantType lockStatus) throws DatabaseException{
    if ((lockStatus == LockGrantType.NEW) || (lockStatus == LockGrantType.WAIT_NEW)) {
            locker.releaseLock(nodeId);
        } else if ((lockStatus == LockGrantType.PROMOTION) || (lockStatus == LockGrantType.WAIT_PROMOTION)) {
            locker.demoteLock(nodeId);
        }
  }


  /**
   * 
   * Locks the logical EOF node for the database.
   */
  // line 1572 "../../../../CursorImpl.ump"
   public void lockEofNode(LockType lockType) throws DatabaseException{
    locker.lock(database.getEofNodeId(), lockType, false, database);
  }


  /**
   * 
   * @throws RunRecoveryExceptionif the underlying environment is invalid.
   */
  // line 1579 "../../../../CursorImpl.ump"
   public void checkEnv() throws RunRecoveryException{
    database.getDbEnvironment().checkIfInvalid();
  }

  // line 1583 "../../../../CursorImpl.ump"
   public CursorImpl getLockerPrev(){
    return lockerPrev;
  }

  // line 1587 "../../../../CursorImpl.ump"
   public CursorImpl getLockerNext(){
    return lockerNext;
  }

  // line 1591 "../../../../CursorImpl.ump"
   public void setLockerPrev(CursorImpl p){
    lockerPrev = p;
  }

  // line 1595 "../../../../CursorImpl.ump"
   public void setLockerNext(CursorImpl n){
    lockerNext = n;
  }


  /**
   * 
   * Dump the cursor for debugging purposes. Dump the bin and dbin that the cursor refers to if verbose is true.
   */
  // line 1602 "../../../../CursorImpl.ump"
   public void dump(boolean verbose){
    System.out.println(dumpToString(verbose));
  }


  /**
   * 
   * dump the cursor for debugging purposes.
   */
  // line 1609 "../../../../CursorImpl.ump"
   public void dump(){
    System.out.println(dumpToString(true));
  }

  // line 1613 "../../../../CursorImpl.ump"
   private String statusToString(byte status){
    switch (status) {
            case CURSOR_NOT_INITIALIZED:
                return "CURSOR_NOT_INITIALIZED";
            case CURSOR_INITIALIZED:
                return "CURSOR_INITIALIZED";
            case CURSOR_CLOSED:
                return "CURSOR_CLOSED";
            default:
                return "UNKNOWN (" + Byte.toString(status) + ")";
        }
  }

  // line 1626 "../../../../CursorImpl.ump"
   public String dumpToString(boolean verbose){
    StringBuffer sb = new StringBuffer();
        sb.append("<Cursor idx=\"").append(index).append("\"");
        if (dupBin != null) {
            sb.append(" dupIdx=\"").append(dupIndex).append("\"");
        }
        sb.append(" status=\"").append(statusToString(status)).append("\"");
        sb.append(">\n");
        if (verbose) {
            sb.append((bin == null) ? "" : bin.dumpString(2, true));
            sb.append((dupBin == null) ? "" : dupBin.dumpString(2, true));
        }
        sb.append("\n</Cursor>");
        return sb.toString();
  }

  // line 1642 "../../../../CursorImpl.ump"
   public void setTestHook(TestHook hook){
    testHook = hook;
  }

  // line 9 "../../../../Latches_CursorImpl.ump"
   public void releaseBIN() throws LatchNotHeldException{
    if (bin != null) {
            bin.releaseLatchIfOwner();
        }
  }

  // line 15 "../../../../Latches_CursorImpl.ump"
   public void latchBINs() throws DatabaseException{
    latchBIN();
        latchDBIN();
  }

  // line 20 "../../../../Latches_CursorImpl.ump"
   public void releaseBINs() throws LatchNotHeldException{
    releaseBIN();
        releaseDBIN();
  }

  // line 25 "../../../../Latches_CursorImpl.ump"
   public void releaseDBIN() throws LatchNotHeldException{
    if (dupBin != null) {
            dupBin.releaseLatchIfOwner();
        }
  }

  // line 31 "../../../../Latches_CursorImpl.ump"
   private boolean checkAlreadyLatched(boolean alreadyLatched){
    if (alreadyLatched) {
            if (dupBin != null) {
                return dupBin.isLatchOwner();
            } else if (bin != null) {
                return bin.isLatchOwner();
            }
        }
        return true;
  }

  // line 9 "../../../../Evictor_CursorImpl.ump"
   public void setAllowEviction(boolean allowed){
    allowEviction = allowed;
  }

  // line 15 "../../../../Evictor_CursorImpl.ump"
   public void evict() throws DatabaseException{
    try {
				  Label202:
latchBINs();
			//this.hook202();
					setTargetBin();
					targetBin.evictLN(targetIndex);

				} finally {

			LabelEvict_1:
releaseBINs();
 ;//

			}
  }

  // line 8 "../../../../Verifier_CursorImpl.ump"
   private void verifyCursor(BIN bin) throws DatabaseException{
    if (!bin.getCursorSet().contains(this)) {
					throw new DatabaseException("BIN cursorSet is inconsistent.");
			}
  }

  // line 6 "../../../../Statistics_CursorImpl.ump"
   public LockStats getLockStats() throws DatabaseException{
    return locker.collectStats(new LockStats());
  }
  /*PLEASE DO NOT EDIT THIS CODE*/
  /*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/
  
  
  
  // line 4 "../../../../CursorImpl_static.ump"
  public static class SearchMode
  {
  
    //------------------------
    // MEMBER VARIABLES
    //------------------------
  
    //------------------------
    // CONSTRUCTOR
    //------------------------
  
    public SearchMode()
    {}
  
    //------------------------
    // INTERFACE
    //------------------------
  
    public void delete()
    {}
  
    // line 13 "../../../../CursorImpl_static.ump"
     private  SearchMode(boolean exactSearch, boolean dataSearch, String name){
      this.exactSearch = exactSearch;
              this.dataSearch = dataSearch;
              this.name = "SearchMode." + name;
    }
  
    // line 32 "../../../../CursorImpl_static.ump"
     public String toString(){
      return name;
    }
    
    //------------------------
    // DEVELOPER CODE - PROVIDED AS-IS
    //------------------------
    
    // line 5 "../../../../CursorImpl_static.ump"
    public static final SearchMode SET = new SearchMode(true, false, "SET") ;
  // line 6 "../../../../CursorImpl_static.ump"
    public static final SearchMode BOTH = new SearchMode(true, true, "BOTH") ;
  // line 7 "../../../../CursorImpl_static.ump"
    public static final SearchMode SET_RANGE = new SearchMode(false, false, "SET_RANGE") ;
  // line 8 "../../../../CursorImpl_static.ump"
    public static final SearchMode BOTH_RANGE = new SearchMode(false, true, "BOTH_RANGE") ;
  // line 9 "../../../../CursorImpl_static.ump"
    private boolean exactSearch ;
  // line 10 "../../../../CursorImpl_static.ump"
    private boolean dataSearch ;
  // line 11 "../../../../CursorImpl_static.ump"
    private String name ;
  
  // line 21 "../../../../CursorImpl_static.ump"
    public final boolean isExactSearch () 
    {
      return exactSearch;
    }
  
  // line 28 "../../../../CursorImpl_static.ump"
    public final boolean isDataSearch () 
    {
      return dataSearch;
    }
  
    
  }  /*PLEASE DO NOT EDIT THIS CODE*/
  /*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/
  
  
  
  // line 35 "../../../../CursorImpl_static.ump"
  public static class KeyChangeStatus
  {
  
    //------------------------
    // MEMBER VARIABLES
    //------------------------
  
    //------------------------
    // CONSTRUCTOR
    //------------------------
  
    public KeyChangeStatus()
    {}
  
    //------------------------
    // INTERFACE
    //------------------------
  
    public void delete()
    {}
  
    // line 45 "../../../../CursorImpl_static.ump"
     public  KeyChangeStatus(OperationStatus status, boolean keyChange){
      this.status = status;
              this.keyChange = keyChange;
    }
    
    //------------------------
    // DEVELOPER CODE - PROVIDED AS-IS
    //------------------------
    
    // line 39 "../../../../CursorImpl_static.ump"
    public OperationStatus status ;
  // line 43 "../../../../CursorImpl_static.ump"
    public boolean keyChange ;
  
    
  }  /*PLEASE DO NOT EDIT THIS CODE*/
  /*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/
  
  
  
  // line 49 "../../../../CursorImpl_static.ump"
  public static class CursorImpl_latchBIN
  {
  
    //------------------------
    // MEMBER VARIABLES
    //------------------------
  
    //------------------------
    // CONSTRUCTOR
    //------------------------
  
    public CursorImpl_latchBIN()
    {}
  
    //------------------------
    // INTERFACE
    //------------------------
  
    public void delete()
    {}
  
    // line 51 "../../../../CursorImpl_static.ump"
    public  CursorImpl_latchBIN(CursorImpl _this){
      this._this = _this;
    }
  
    // line 54 "../../../../CursorImpl_static.ump"
    public BIN execute() throws DatabaseException{
      // try {
                  Label244: //this.hook244();
   //                   Label245: //this.hook245();
  //                    throw new ReturnObject(_this.bin);
   //               Label244_1: throw ReturnHack.returnObject;
   //           }
   //           catch (ReturnObject r) {
    //              return (BIN) r.value;
       //       }
  
  	        return null;
    }
    
    //------------------------
    // DEVELOPER CODE - PROVIDED AS-IS
    //------------------------
    
    // line 66 "../../../../CursorImpl_static.ump"
    protected CursorImpl _this ;
  // line 67 "../../../../CursorImpl_static.ump"
    protected BIN waitingOn ;
  
    
  }  /*PLEASE DO NOT EDIT THIS CODE*/
  /*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/
  
  
  
  // line 70 "../../../../CursorImpl_static.ump"
  public static class CursorImpl_latchDBIN
  {
  
    //------------------------
    // MEMBER VARIABLES
    //------------------------
  
    //------------------------
    // CONSTRUCTOR
    //------------------------
  
    public CursorImpl_latchDBIN()
    {}
  
    //------------------------
    // INTERFACE
    //------------------------
  
    public void delete()
    {}
  
    // line 72 "../../../../CursorImpl_static.ump"
    public  CursorImpl_latchDBIN(CursorImpl _this){
      this._this = _this;
    }
  
    // line 75 "../../../../CursorImpl_static.ump"
    public DBIN execute() throws DatabaseException{
      //          try {
                  Label246:; //this.hook246();
                  Label247: ;
                  Label246_1: ;
  //                throw new ReturnObject(_this.dupBin);
  
  //throw ReturnHack.returnObject;
          //    }
        //      catch (ReturnObject r) {
                  return null; //(DBIN) r.value;
      //        }
    }
    
    //------------------------
    // DEVELOPER CODE - PROVIDED AS-IS
    //------------------------
    
    // line 87 "../../../../CursorImpl_static.ump"
    protected CursorImpl _this ;
  // line 88 "../../../../CursorImpl_static.ump"
    protected BIN waitingOn ;
  
    
  }  /*PLEASE DO NOT EDIT THIS CODE*/
  /*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/
  
  
  
  // line 91 "../../../../CursorImpl_static.ump"
  public static class CursorImpl_lockNextKeyForInsert
  {
  
    //------------------------
    // MEMBER VARIABLES
    //------------------------
  
    //------------------------
    // CONSTRUCTOR
    //------------------------
  
    public CursorImpl_lockNextKeyForInsert()
    {}
  
    //------------------------
    // INTERFACE
    //------------------------
  
    public void delete()
    {}
  
    // line 93 "../../../../CursorImpl_static.ump"
    public  CursorImpl_lockNextKeyForInsert(CursorImpl _this, DatabaseEntry key, DatabaseEntry data){
      this._this = _this;
              this.key = key;
              this.data = data;
    }
  
    // line 98 "../../../../CursorImpl_static.ump"
    public void execute() throws DatabaseException{
      tempKey = new DatabaseEntry(key.getData(), key.getOffset(), key.getSize());
              tempData = new DatabaseEntry(data.getData(), data.getOffset(), data.getSize());
              tempKey.setPartial(0, 0, true);
              tempData.setPartial(0, 0, true);
              lockedNextKey = false;
              searchMode = _this.database.getSortedDuplicates() ? SearchMode.BOTH_RANGE : SearchMode.SET_RANGE;
              Label248: //this.hook248();
                  searchResult = _this.searchAndPosition(tempKey, tempData, searchMode, LockType.RANGE_INSERT);
              if ((searchResult & _this.FOUND) != 0 && (searchResult & _this.FOUND_LAST) == 0) {
                  {}
                  if ((searchResult & _this.EXACT_KEY) != 0) {
                      status = _this.getNext(tempKey, tempData, LockType.RANGE_INSERT, true, true);
                  } else {
                      status = _this.getNextNoDup(tempKey, tempData, LockType.RANGE_INSERT, true, true);
                  }
                  if (status == OperationStatus.SUCCESS) {
                      lockedNextKey = true;
                  }
                  Label249: ;//this.hook249();
              }
              Label248_1:
                  if (!lockedNextKey) {
                      _this.lockEofNode(LockType.RANGE_INSERT);
                  }
    }
    
    //------------------------
    // DEVELOPER CODE - PROVIDED AS-IS
    //------------------------
    
    // line 123 "../../../../CursorImpl_static.ump"
    protected CursorImpl _this ;
  // line 124 "../../../../CursorImpl_static.ump"
    protected DatabaseEntry key ;
  // line 125 "../../../../CursorImpl_static.ump"
    protected DatabaseEntry data ;
  // line 126 "../../../../CursorImpl_static.ump"
    protected DatabaseEntry tempKey ;
  // line 127 "../../../../CursorImpl_static.ump"
    protected DatabaseEntry tempData ;
  // line 128 "../../../../CursorImpl_static.ump"
    protected boolean lockedNextKey ;
  // line 129 "../../../../CursorImpl_static.ump"
    protected SearchMode searchMode ;
  // line 130 "../../../../CursorImpl_static.ump"
    protected boolean latched ;
  // line 131 "../../../../CursorImpl_static.ump"
    protected int searchResult ;
  // line 132 "../../../../CursorImpl_static.ump"
    protected OperationStatus status ;
  
    
  }  /*PLEASE DO NOT EDIT THIS CODE*/
  /*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/
  
  
  
  // line 135 "../../../../CursorImpl_static.ump"
  public static class CursorImpl_getNextDuplicate
  {
  
    //------------------------
    // MEMBER VARIABLES
    //------------------------
  
    //------------------------
    // CONSTRUCTOR
    //------------------------
  
    public CursorImpl_getNextDuplicate()
    {}
  
    //------------------------
    // INTERFACE
    //------------------------
  
    public void delete()
    {}
  
    // line 137 "../../../../CursorImpl_static.ump"
    public  CursorImpl_getNextDuplicate(CursorImpl _this, DatabaseEntry foundKey, DatabaseEntry foundData, LockType lockType, boolean forward, boolean alreadyLatched){
      this._this = _this;
              this.foundKey = foundKey;
              this.foundData = foundData;
              this.lockType = lockType;
              this.forward = forward;
              this.alreadyLatched = alreadyLatched;
    }
  
    // line 145 "../../../../CursorImpl_static.ump"
    public OperationStatus execute() throws DatabaseException{
      // try {
                  assert _this.assertCursorState(true): _this.dumpToString(true);
                  Label250: //this.hook250();
                      try {
                          while (_this.dupBin != null) {
                              Label251: //this.hook251();
                                  Label279: //this.hook279();
                                  if ((forward && ++_this.dupIndex < _this.dupBin.getNEntries()) || (!forward && --_this.dupIndex > -1)) {
                                      ret = OperationStatus.SUCCESS;
                                      if (foundKey != null) {
                                          ret = _this.getCurrentAlreadyLatched(foundKey, foundData, lockType, forward);
                                      } else {
                                          Label252: ;//this.hook252();
                                      }
                                      if (ret == OperationStatus.SUCCESS) {
                                          _this.incrementLNCount();
                                          return ret;
                                      } else {
                                          Label253: //this.hook253();
                                              if (_this.dupBinToBeRemoved != null) {
                                                  _this.flushDBINToBeRemoved();
                                              }
                                          continue;
                                      }
                                  }
                              else {
                                  if (_this.dupBinToBeRemoved != null) {
                                      _this.flushDBINToBeRemoved();
                                  }
                                  _this.dupBinToBeRemoved = _this.dupBin;
                                  _this.dupBin = null;
                                  Label255: //this.hook255();
                                      Label275: //this.hook275();
                                      Label254: //this.hook254();
                                      {}
                                  if (forward) {
                                      newDupBin = (DBIN) _this.database.getTree().getNextBin(_this.dupBinToBeRemoved, true);
                                  } else {
                                      newDupBin = (DBIN) _this.database.getTree().getPrevBin(_this.dupBinToBeRemoved, true);
                                  }
                                  if (newDupBin == null) {
                                      return OperationStatus.NOTFOUND;
                                  } else {
                                      if (forward) {
                                          _this.dupIndex = -1;
                                      } else {
                                          _this.dupIndex = newDupBin.getNEntries();
                                      }
                                      _this.addCursor(newDupBin);
                                      _this.dupBin = newDupBin;
                                      Label256: ;//this.hook256();
                                  }
                              }
                          }
                      }
                  finally {
                      Label257: //this.hook257();
                          if (_this.dupBinToBeRemoved != null) {
                              _this.flushDBINToBeRemoved();
                          }
                  }
                  return OperationStatus.NOTFOUND;
           //   } catch (ReturnObject r) {
            //      return (OperationStatus) r.value;
             // }
    }
    
    //------------------------
    // DEVELOPER CODE - PROVIDED AS-IS
    //------------------------
    
    // line 211 "../../../../CursorImpl_static.ump"
    protected CursorImpl _this ;
  // line 212 "../../../../CursorImpl_static.ump"
    protected DatabaseEntry foundKey ;
  // line 213 "../../../../CursorImpl_static.ump"
    protected DatabaseEntry foundData ;
  // line 214 "../../../../CursorImpl_static.ump"
    protected LockType lockType ;
  // line 215 "../../../../CursorImpl_static.ump"
    protected boolean forward ;
  // line 216 "../../../../CursorImpl_static.ump"
    protected boolean alreadyLatched ;
  // line 217 "../../../../CursorImpl_static.ump"
    protected OperationStatus ret ;
  // line 218 "../../../../CursorImpl_static.ump"
    protected TreeWalkerStatsAccumulator treeStatsAccumulator ;
  // line 219 "../../../../CursorImpl_static.ump"
    protected DIN duplicateRoot ;
  // line 220 "../../../../CursorImpl_static.ump"
    protected DupCountLN dcl ;
  // line 221 "../../../../CursorImpl_static.ump"
    protected DBIN newDupBin ;
  
    
  }  /*PLEASE DO NOT EDIT THIS CODE*/
  /*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/
  
  
  
  @MethodObject
  // line 224 "../../../../CursorImpl_static.ump"
  // line 4 "../../../../INCompressor_CursorImpl_inner.ump"
  public static class CursorImpl_fetchCurrent
  {
  
    //------------------------
    // MEMBER VARIABLES
    //------------------------
  
    //------------------------
    // CONSTRUCTOR
    //------------------------
  
    public CursorImpl_fetchCurrent()
    {}
  
    //------------------------
    // INTERFACE
    //------------------------
  
    public void delete()
    {}
  
    // line 226 "../../../../CursorImpl_static.ump"
    public  CursorImpl_fetchCurrent(CursorImpl _this, DatabaseEntry foundKey, DatabaseEntry foundData, LockType lockType, boolean first){
      this._this = _this;
              this.foundKey = foundKey;
              this.foundData = foundData;
              this.lockType = lockType;
              this.first = first;
    }
  
    // line 233 "../../../../CursorImpl_static.ump"
    public OperationStatus execute() throws DatabaseException{
      try {
                  treeStatsAccumulator = _this.getTreeStatsAccumulator();
                  duplicateFetch = _this.setTargetBin();
                  if (_this.targetBin == null) {
                      return OperationStatus.NOTFOUND;
                  }
                  Label259: //this.hook259();
                      n = null;
                  if (_this.targetIndex < 0 || _this.targetIndex >= _this.targetBin.getNEntries() || _this.targetBin.isEntryKnownDeleted(_this.targetIndex)) {} else {
                      if (_this.targetBin.isEntryPendingDeleted(_this.targetIndex)) {
                          Label280:
  envImpl=_this.database.getDbEnvironment();
          envImpl.addToCompressorQueue(_this.targetBin,new Key(_this.targetBin.getKey(_this.targetIndex)),false);
          //original();
   ;//this.hook280();
                      }
                      Label260: ; //this.hook260();
                      n = _this.targetBin.fetchTarget(_this.targetIndex);
                      Label260_1: ; //this.hook260();
  
                  }
                  if (n == null) {
                      if (treeStatsAccumulator != null) {
                          treeStatsAccumulator.incrementDeletedLNCount();
                      }
                      Label261: //this.hook261();
                          return OperationStatus.KEYEMPTY;
                  }
                  _this.addCursor(_this.targetBin);
                  if (n.containsDuplicates()) {
                      assert!duplicateFetch;
                      duplicateRoot = (DIN) n;
                      Label262: //this.hook262();
                          if (_this.positionFirstOrLast(first, duplicateRoot)) {
                              Label263:; //this.hook263();
                              _this.fetchCurrent(foundKey, foundData, lockType, first);
                              Label263_1:; // end of hook263();
                          }
                      else {
                          return OperationStatus.NOTFOUND;
                      }
                  }
                  ln = (LN) n;
                  assert TestHookExecute.doHookIfSet(_this.testHook);
                  lockResult = _this.lockLN(ln, lockType);
                  Label258: ; //this.hook258();
                  ln = lockResult.getLN();
                  lnData = (ln != null) ? ln.getData() : null;
                  if (ln == null || lnData == null) {
                      if (treeStatsAccumulator != null) {
                          treeStatsAccumulator.incrementDeletedLNCount();
                      }
                      throw new ReturnObject(OperationStatus.KEYEMPTY);
                  }
                  duplicateFetch = _this.setTargetBin();
                  if (duplicateFetch) {
                      if (foundData != null) {
                          _this.setDbt(foundData, _this.targetBin.getKey(_this.targetIndex));
                      }
                      if (foundKey != null) {
                          _this.setDbt(foundKey, _this.targetBin.getDupKey());
                      }
                  } else {
                      if (foundData != null) {
                          _this.setDbt(foundData, lnData);
                      }
                      if (foundKey != null) {
                          _this.setDbt(foundKey, _this.targetBin.getKey(_this.targetIndex));
                      }
                  }
  Label258_1: ;
                  return OperationStatus.SUCCESS;
  
  
              } catch (ReturnObject r) {
                  return (OperationStatus) r.value;
              }
    }
    
    //------------------------
    // DEVELOPER CODE - PROVIDED AS-IS
    //------------------------
    
    // line 307 "../../../../CursorImpl_static.ump"
    protected CursorImpl _this ;
  // line 308 "../../../../CursorImpl_static.ump"
    protected DatabaseEntry foundKey ;
  // line 309 "../../../../CursorImpl_static.ump"
    protected DatabaseEntry foundData ;
  // line 310 "../../../../CursorImpl_static.ump"
    protected LockType lockType ;
  // line 311 "../../../../CursorImpl_static.ump"
    protected boolean first ;
  // line 312 "../../../../CursorImpl_static.ump"
    protected TreeWalkerStatsAccumulator treeStatsAccumulator ;
  // line 313 "../../../../CursorImpl_static.ump"
    protected boolean duplicateFetch ;
  // line 314 "../../../../CursorImpl_static.ump"
    protected Node n ;
  // line 315 "../../../../CursorImpl_static.ump"
    protected EnvironmentImpl envImpl ;
  // line 316 "../../../../CursorImpl_static.ump"
    protected DIN duplicateRoot ;
  // line 317 "../../../../CursorImpl_static.ump"
    protected LN ln ;
  // line 318 "../../../../CursorImpl_static.ump"
    protected LockResult lockResult ;
  // line 319 "../../../../CursorImpl_static.ump"
    protected byte[] lnData ;
  
    
  }  
  //------------------------
  // DEVELOPER CODE - PROVIDED AS-IS
  //------------------------
  
  // line 42 "../../../../CursorImpl.ump"
  private static final byte CURSOR_NOT_INITIALIZED = 1 ;
// line 44 "../../../../CursorImpl.ump"
  private static final byte CURSOR_INITIALIZED = 2 ;
// line 46 "../../../../CursorImpl.ump"
  private static final byte CURSOR_CLOSED = 3 ;
// line 48 "../../../../CursorImpl.ump"
  private static final String TRACE_DELETE = "Delete" ;
// line 50 "../../../../CursorImpl.ump"
  private static final String TRACE_MOD = "Mod:" ;
// line 52 "../../../../CursorImpl.ump"
  volatile private BIN bin ;
// line 54 "../../../../CursorImpl.ump"
  volatile private int index ;
// line 56 "../../../../CursorImpl.ump"
  volatile private DBIN dupBin ;
// line 58 "../../../../CursorImpl.ump"
  volatile private int dupIndex ;
// line 60 "../../../../CursorImpl.ump"
  volatile private BIN binToBeRemoved ;
// line 62 "../../../../CursorImpl.ump"
  volatile private DBIN dupBinToBeRemoved ;
// line 64 "../../../../CursorImpl.ump"
  private BIN targetBin ;
// line 66 "../../../../CursorImpl.ump"
  private int targetIndex ;
// line 68 "../../../../CursorImpl.ump"
  private byte[] dupKey ;
// line 70 "../../../../CursorImpl.ump"
  private DatabaseImpl database ;
// line 72 "../../../../CursorImpl.ump"
  private Locker locker ;
// line 74 "../../../../CursorImpl.ump"
  private CursorImpl lockerPrev ;
// line 76 "../../../../CursorImpl.ump"
  private CursorImpl lockerNext ;
// line 78 "../../../../CursorImpl.ump"
  private boolean retainNonTxnLocks ;
// line 80 "../../../../CursorImpl.ump"
  private byte status ;
// line 82 "../../../../CursorImpl.ump"
  private TestHook testHook ;
// line 84 "../../../../CursorImpl.ump"
  private boolean nonCloning = false ;
// line 86 "../../../../CursorImpl.ump"
  private int thisId ;
// line 88 "../../../../CursorImpl.ump"
  private static long lastAllocatedId = 0 ;
// line 90 "../../../../CursorImpl.ump"
  private ThreadLocal treeStatsAccumulatorTL = new ThreadLocal() ;
// line 92 "../../../../CursorImpl.ump"
  public static final int FOUND = 0x1 ;
// line 94 "../../../../CursorImpl.ump"
  public static final int EXACT_KEY = 0x2 ;
// line 96 "../../../../CursorImpl.ump"
  public static final int EXACT_DATA = 0x4 ;
// line 98 "../../../../CursorImpl.ump"
  public static final int FOUND_LAST = 0x8 ;
// line 5 "../../../../Evictor_CursorImpl.ump"
  private boolean allowEviction = true ;
// line 5 "../../../../Verifier_CursorImpl.ump"
  private static final boolean DEBUG = false ;

  
}