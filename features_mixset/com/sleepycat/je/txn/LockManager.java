/*PLEASE DO NOT EDIT THIS CODE*/
/*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/

package com.sleepycat.je.txn;
import de.ovgu.cide.jakutil.*;
import com.sleepycat.je.dbi.RangeRestartException;
import com.sleepycat.je.dbi.MemoryBudget;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.dbi.EnvConfigObserver;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.DatabaseImpl;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.RunRecoveryException;
import com.sleepycat.je.LockStats;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.DatabaseException;
import java.util.Set;
import java.util.Map;
import java.util.Iterator;
import java.util.HashSet;
import java.util.HashMap;
import com.sleepycat.je.dbi.*;

// line 3 "../../../../LockManager.ump"
// line 3 "../../../../LockManager_static.ump"
// line 3 "../../../../MemoryBudget_LockManager.ump"
public abstract class LockManager implements EnvConfigObserver
{

  //------------------------
  // MEMBER VARIABLES
  //------------------------

  //------------------------
  // CONSTRUCTOR
  //------------------------

  public LockManager()
  {}

  //------------------------
  // INTERFACE
  //------------------------

  public void delete()
  {}

  // line 36 "../../../../LockManager.ump"
   public  LockManager(EnvironmentImpl envImpl) throws DatabaseException{
    DbConfigManager configMgr = envImpl.getConfigManager();
	this.hook779(configMgr);
	lockTables = new Map[nLockTables];
	this.hook770();
	for (int i = 0; i < nLockTables; i++) {
	    lockTables[i] = new HashMap();
	    this.hook771(envImpl, i);
	}
	this.envImpl = envImpl;
	memoryBudget = envImpl.getMemoryBudget();
	this.hook774();
	envConfigUpdate(configMgr);
	envImpl.addConfigObserver(this);
  }


  /**
   * 
   * Process notifications of mutable property changes.
   */
  // line 55 "../../../../LockManager.ump"
   public void envConfigUpdate(DbConfigManager configMgr) throws DatabaseException{
    LockInfo.setDeadlockStackTrace(configMgr.getBoolean(EnvironmentParams.TXN_DEADLOCK_STACK_TRACE));
	setLockTableDump(configMgr.getBoolean(EnvironmentParams.TXN_DUMPLOCKS));
  }


  /**
   * 
   * Called when the je.txn.dumpLocks property is changed.
   */
  // line 63 "../../../../LockManager.ump"
   static  void setLockTableDump(boolean enable){
    lockTableDump = enable;
  }

  // line 67 "../../../../LockManager.ump"
   protected int getLockTableIndex(Long nodeId){
    return ((int) nodeId.longValue()) % nLockTables;
  }

  // line 71 "../../../../LockManager.ump"
   protected int getLockTableIndex(long nodeId){
    return ((int) nodeId) % nLockTables;
  }


  /**
   * 
   * Attempt to acquire a lock of <i>type</i> on <i>nodeId</i>. If the lock acquisition would result in a deadlock, throw an exception.<br> If the requested lock is not currently available, block until it is or until timeout milliseconds have elapsed.<br> If a lock of <i>type</i> is already held, return EXISTING.<br> If a WRITE lock is held and a READ lock is requested, return PROMOTION.<br> If a lock request is for a lock that is not currently held, return either NEW or DENIED depending on whether the lock is granted or not.<br>
   * @param nodeIdThe NodeId to lock.
   * @param lockerThe Locker to lock this on behalf of.
   * @param typeThe lock type requested.
   * @param timeoutmilliseconds to time out after if lock couldn't be obtained. 0means block indefinitely. Not used if nonBlockingRequest is true.
   * @param nonBlockingRequestif true, means don't block if lock can't be acquired, andignore the timeout parameter.
   * @return a LockGrantType indicating whether the request was fulfilled ornot. LockGrantType.NEW means the lock grant was fulfilled and the caller did not previously hold the lock. PROMOTION means the lock was granted and it was a promotion from READ to WRITE. EXISTING means the lock was already granted (not a promotion). DENIED means the lock was not granted either because the timeout passed without acquiring the lock or timeout was -1 and the lock was not immediately available.
   * @throws DeadlockExceptionif acquiring the lock would result in a deadlock.
   */
  // line 86 "../../../../LockManager.ump"
   public LockGrantType lock(long nodeId, Locker locker, LockType type, long timeout, boolean nonBlockingRequest, DatabaseImpl database) throws DeadlockException,DatabaseException{
    assert timeout >= 0;
	synchronized (locker) {
	    Long nid = new Long(nodeId);
	    LockAttemptResult result = attemptLock(nid, locker, type, nonBlockingRequest);
	    if (result.success || result.lockGrant == LockGrantType.DENIED) {
		return result.lockGrant;
	    }
	    this.hook772(nonBlockingRequest);
	    assert !nonBlockingRequest;
	    try {
		boolean doWait = true;
		if (locker.isTimedOut()) {
		    if (validateOwnership(nid, locker, type, true, memoryBudget)) {
			doWait = false;
		    } else {
			String errMsg = makeTimeoutMsg("Transaction", locker, nodeId, type, result.lockGrant,
				result.useLock, locker.getTxnTimeOut(), locker.getTxnStartMillis(),
				System.currentTimeMillis(), database);
			throw new DeadlockException(errMsg);
		    }
		}
		boolean keepTime = (timeout > 0);
		long startTime = (keepTime ? System.currentTimeMillis() : 0);
		while (doWait) {
		    locker.setWaitingFor(result.useLock);
		    try {
			locker.wait(timeout);
		    } catch (InterruptedException IE) {
			throw new RunRecoveryException(envImpl, IE);
		    }
		    boolean lockerTimedOut = locker.isTimedOut();
		    long now = System.currentTimeMillis();
		    boolean thisLockTimedOut = (keepTime && (now - startTime > timeout));
		    boolean isRestart = (result.lockGrant == LockGrantType.WAIT_RESTART);
		    if (validateOwnership(nid, locker, type, lockerTimedOut || thisLockTimedOut || isRestart,
			    memoryBudget)) {
			break;
		    } else {
			if (isRestart) {
			    throw rangeRestartException;
			}
			if (thisLockTimedOut) {
			    locker.setOnlyAbortable();
			    String errMsg = makeTimeoutMsg("Lock", locker, nodeId, type, result.lockGrant,
				    result.useLock, timeout, startTime, now, database);
			    throw new DeadlockException(errMsg);
			}
			if (lockerTimedOut) {
			    locker.setOnlyAbortable();
			    String errMsg = makeTimeoutMsg("Transaction", locker, nodeId, type, result.lockGrant,
				    result.useLock, locker.getTxnTimeOut(), locker.getTxnStartMillis(), now, database);
			    throw new DeadlockException(errMsg);
			}
		    }
		}
	    } finally {
		locker.setWaitingFor(null);
		assert EnvironmentImpl.maybeForceYield();
	    }
	    locker.addLock(nid, result.useLock, type, result.lockGrant);
	    return result.lockGrant;
	}
  }

  // line 155 "../../../../LockManager.ump"
   protected LockAttemptResult attemptLockInternal(Long nodeId, Locker locker, LockType type, boolean nonBlockingRequest, int lockTableIndex) throws DatabaseException{
    Map lockTable = lockTables[lockTableIndex];
	Lock useLock = (Lock) lockTable.get(nodeId);
	if (useLock == null) {
	    useLock = new Lock(nodeId);
	    lockTable.put(nodeId, useLock);
	    this.hook780(lockTableIndex);
	}
	LockGrantType lockGrant = useLock.lock(type, locker, nonBlockingRequest, memoryBudget, lockTableIndex);
	boolean success = false;
	if ((lockGrant == LockGrantType.NEW) || (lockGrant == LockGrantType.PROMOTION)) {
	    locker.addLock(nodeId, useLock, type, lockGrant);
	    success = true;
	} else if (lockGrant == LockGrantType.EXISTING) {
	    success = true;
	} else if (lockGrant == LockGrantType.DENIED) {
	} else {
	    this.hook775();
	}
	return new LockAttemptResult(useLock, lockGrant, success);
  }


  /**
   * 
   * Create a informative lock or txn timeout message.
   */
   protected abstract String makeTimeoutMsg(String lockOrTxn, Locker locker, long nodeId, LockType type, LockGrantType grantType, Lock useLock, long timeout, long start, long now, DatabaseImpl database) throws DatabaseException;


  /**
   * 
   * Do the real work of creating an lock or txn timeout message.
   */
  // line 188 "../../../../LockManager.ump"
   protected String makeTimeoutMsgInternal(String lockOrTxn, Locker locker, long nodeId, LockType type, LockGrantType grantType, Lock useLock, long timeout, long start, long now, DatabaseImpl database){
    if (lockTableDump) {
	    System.out.println("++++++++++ begin lock table dump ++++++++++");
	    for (int i = 0; i < nLockTables; i++) {
		StringBuffer sb = new StringBuffer();
		dumpToStringNoLatch(sb, i);
		System.out.println(sb.toString());
	    }
	    System.out.println("++++++++++ end lock table dump ++++++++++");
	}
	StringBuffer sb = new StringBuffer();
	sb.append(lockOrTxn);
	sb.append(" expired. Locker ").append(locker);
	sb.append(": waited for lock");
	if (database != null) {
	    sb.append(" on database=").append(database.getDebugName());
	}
	sb.append(" node=").append(nodeId);
	sb.append(" type=").append(type);
	sb.append(" grant=").append(grantType);
	sb.append(" timeoutMillis=").append(timeout);
	sb.append(" startTime=").append(start);
	sb.append(" endTime=").append(now);
	sb.append("\nOwners: ").append(useLock.getOwnersClone());
	sb.append("\nWaiters: ").append(useLock.getWaitersListClone()).append("\n");
	StringBuffer deadlockInfo = findDeadlock(useLock, locker);
	if (deadlockInfo != null) {
	    sb.append(deadlockInfo);
	}
	return sb.toString();
  }


  /**
   * 
   * Release a lock and possibly notify any waiters that they have been granted the lock.
   * @param nodeIdThe node ID of the lock to release.
   * @return true if the lock is released successfully, false if the lock isnot currently being held.
   */
  // line 225 "../../../../LockManager.ump"
  public boolean release(long nodeId, Locker locker) throws DatabaseException{
    return release(nodeId, null, locker, true);
  }


  /**
   * 
   * Release a lock and possibly notify any waiters that they have been granted the lock.
   * @param lockThe lock to release
   * @return true if the lock is released successfully, false if the lock isnot currently being held.
   */
  // line 234 "../../../../LockManager.ump"
  public boolean release(Lock lock, Locker locker) throws DatabaseException{
    return release(-1, lock, locker, false);
  }


  /**
   * 
   * Do the work of releasing a lock and notifying any waiters that they have been granted the lock.
   * @param lockThe lock to release. If null, use nodeId to find lock
   * @param nodeIdThe node ID of the lock to release, if lock is null. May notbe valid if lock is not null. MUST be valid if removeFromLocker is true
   * @param locker
   * @param removeFromLockertrue if we're responsible for
   * @return true if the lock is released successfully, false if the lock isnot currently being held.
   */
  // line 246 "../../../../LockManager.ump"
   private boolean release(long nodeId, Lock lock, Locker locker, boolean removeFromLocker) throws DatabaseException{
    synchronized (locker) {
	    Set newOwners = releaseAndFindNotifyTargets(nodeId, lock, locker, removeFromLocker);
	    if (newOwners == null) {
		return false;
	    }
	    if (newOwners.size() > 0) {
		Iterator iter = newOwners.iterator();
		while (iter.hasNext()) {
		    Locker lockerToNotify = (Locker) iter.next();
		    synchronized (lockerToNotify) {
			lockerToNotify.notifyAll();
		    }
		    assert EnvironmentImpl.maybeForceYield();
		}
	    }
	    return true;
	}
  }


  /**
   * 
   * Release the lock, and return the set of new owners to notify, if any.
   * @return null if the lock does not exist or the given locker was not theowner, a non-empty set if owners should be notified after releasing, an empty set if no notification is required.
   */
   protected abstract Set releaseAndFindNotifyTargets(long nodeId, Lock lock, Locker locker, boolean removeFromLocker) throws DatabaseException;


  /**
   * 
   * Do the real work of releaseAndFindNotifyTargets
   */
  // line 277 "../../../../LockManager.ump"
   protected Set releaseAndFindNotifyTargetsInternal(long nodeId, Lock lock, Locker locker, boolean removeFromLocker, int lockTableIndex) throws DatabaseException{
    Lock useLock = lock;
	Map lockTable = lockTables[lockTableIndex];
	if (useLock == null) {
	    useLock = (Lock) lockTable.get(new Long(nodeId));
	}
	if (useLock == null) {
	    return null;
	}
	Set lockersToNotify = useLock.release(locker, memoryBudget, lockTableIndex);
	if (lockersToNotify == null) {
	    return null;
	}
	if (removeFromLocker) {
	    assert nodeId != -1;
	    locker.removeLock(nodeId, useLock);
	}
	if ((useLock.nWaiters() == 0) && (useLock.nOwners() == 0)) {
	    lockTables[lockTableIndex].remove(useLock.getNodeId());
	    this.hook781(lockTableIndex);
	}
	return lockersToNotify;
  }


  /**
   * 
   * Transfer ownership a lock from one locker to another locker. We're not sending any notification to the waiters on the lock table, and the past and present owner should be ready for the transfer.
   */
  public abstract void transfer(long nodeId, Locker owningLocker, Locker destLocker, boolean demoteToRead) throws DatabaseException;


  /**
   * 
   * Do the real work of transfer
   */
  // line 311 "../../../../LockManager.ump"
   protected void transferInternal(long nodeId, Locker owningLocker, Locker destLocker, boolean demoteToRead, int lockTableIndex) throws DatabaseException{
    Map lockTable = lockTables[lockTableIndex];
	Lock useLock = (Lock) lockTable.get(new Long(nodeId));
	assert useLock != null : "Transfer, lock " + nodeId + " was null";
	if (demoteToRead) {
	    useLock.demote(owningLocker);
	}
	useLock.transfer(owningLocker, destLocker, memoryBudget, lockTableIndex);
	owningLocker.removeLock(nodeId, useLock);
  }


  /**
   * 
   * Transfer ownership a lock from one locker to a set of other txns, cloning the lock as necessary. This will always be demoted to read, as we can't have multiple locker owners any other way. We're not sending any notification to the waiters on the lock table, and the past and present owners should be ready for the transfer.
   */
  public abstract void transferMultiple(long nodeId, Locker owningLocker, Locker [] destLockers) throws DatabaseException;


  /**
   * 
   * Do the real work of transferMultiple
   */
  // line 331 "../../../../LockManager.ump"
   protected void transferMultipleInternal(long nodeId, Locker owningLocker, Locker [] destLockers, int lockTableIndex) throws DatabaseException{
    Map lockTable = lockTables[lockTableIndex];
	Lock useLock = (Lock) lockTable.get(new Long(nodeId));
	assert useLock != null : "Transfer, lock " + nodeId + " was null";
	useLock.demote(owningLocker);
	useLock.transferMultiple(owningLocker, destLockers, memoryBudget, lockTableIndex);
	owningLocker.removeLock(nodeId, useLock);
  }


  /**
   * 
   * Demote a lock from write to read. Call back to the owning locker to move this to its read collection.
   * @param lockThe lock to release. If null, use nodeId to find lock
   * @param locker
   */
  public abstract void demote(long nodeId, Locker locker) throws DatabaseException;


  /**
   * 
   * Do the real work of demote.
   */
  // line 350 "../../../../LockManager.ump"
   protected void demoteInternal(long nodeId, Locker locker, int lockTableIndex) throws DatabaseException{
    Map lockTable = lockTables[lockTableIndex];
	Lock useLock = (Lock) lockTable.get(new Long(nodeId));
	useLock.demote(locker);
	locker.moveWriteToReadLock(nodeId, useLock);
  }


  /**
   * 
   * Test the status of the lock on nodeId. If any transaction holds any lock on it, true is returned. If no transaction holds a lock on it, false is returned. This method is only used by unit tests.
   * @param nodeIdThe NodeId to check.
   * @return true if any transaction holds any lock on the nodeid. false if nolock is held by any transaction.
   */
  public abstract boolean isLocked(Long nodeId) throws DatabaseException;


  /**
   * 
   * Do the real work of isLocked.
   */
  // line 367 "../../../../LockManager.ump"
   protected boolean isLockedInternal(Long nodeId, int lockTableIndex){
    Map lockTable = lockTables[lockTableIndex];
	Lock entry = (Lock) lockTable.get(nodeId);
	if (entry == null) {
	    return false;
	}
	return entry.nOwners() != 0;
  }


  /**
   * 
   * Return true if this locker owns this a lock of this type on given node. This method is only used by unit tests.
   */
  public abstract boolean isOwner(Long nodeId, Locker locker, LockType type) throws DatabaseException;


  /**
   * 
   * Do the real work of isOwner.
   */
  // line 384 "../../../../LockManager.ump"
   protected boolean isOwnerInternal(Long nodeId, Locker locker, LockType type, int lockTableIndex){
    Map lockTable = lockTables[lockTableIndex];
	Lock entry = (Lock) lockTable.get(nodeId);
	if (entry == null) {
	    return false;
	}
	return entry.isOwner(locker, type);
  }


  /**
   * 
   * Return true if this locker is waiting on this lock. This method is only used by unit tests.
   */
  public abstract boolean isWaiter(Long nodeId, Locker locker) throws DatabaseException;


  /**
   * 
   * Do the real work of isWaiter.
   */
  // line 401 "../../../../LockManager.ump"
   protected boolean isWaiterInternal(Long nodeId, Locker locker, int lockTableIndex){
    Map lockTable = lockTables[lockTableIndex];
	Lock entry = (Lock) lockTable.get(nodeId);
	if (entry == null) {
	    return false;
	}
	return entry.isWaiter(locker);
  }


  /**
   * 
   * Return the number of waiters for this lock.
   */
  public abstract int nWaiters(Long nodeId) throws DatabaseException;


  /**
   * 
   * Do the real work of nWaiters.
   */
  // line 418 "../../../../LockManager.ump"
   protected int nWaitersInternal(Long nodeId, int lockTableIndex){
    Map lockTable = lockTables[lockTableIndex];
	Lock entry = (Lock) lockTable.get(nodeId);
	if (entry == null) {
	    return -1;
	}
	return entry.nWaiters();
  }


  /**
   * 
   * Return the number of owners of this lock.
   */
  public abstract int nOwners(Long nodeId) throws DatabaseException;


  /**
   * 
   * Do the real work of nWaiters.
   */
  // line 435 "../../../../LockManager.ump"
   protected int nOwnersInternal(Long nodeId, int lockTableIndex){
    Map lockTable = lockTables[lockTableIndex];
	Lock entry = (Lock) lockTable.get(nodeId);
	if (entry == null) {
	    return -1;
	}
	return entry.nOwners();
  }


  /**
   * 
   * @return the transaction that owns the write lock for this
   */
  public abstract Locker getWriteOwnerLocker(Long nodeId) throws DatabaseException;


  /**
   * 
   * Do the real work of getWriteOwnerLocker.
   */
  // line 452 "../../../../LockManager.ump"
   protected Locker getWriteOwnerLockerInternal(Long nodeId, int lockTableIndex) throws DatabaseException{
    Map lockTable = lockTables[lockTableIndex];
	Lock lock = (Lock) lockTable.get(nodeId);
	if (lock == null) {
	    return null;
	} else if (lock.nOwners() > 1) {
	    return null;
	} else {
	    return lock.getWriteOwnerLocker();
	}
  }

  // line 468 "../../../../LockManager.ump"
   protected boolean validateOwnershipInternal(Long nodeId, Locker locker, LockType type, boolean flushFromWaiters, MemoryBudget mb, int lockTableIndex) throws DatabaseException{
    if (isOwnerInternal(nodeId, locker, type, lockTableIndex)) {
	    return true;
	}
	if (flushFromWaiters) {
	    Lock entry = (Lock) lockTables[lockTableIndex].get(nodeId);
	    if (entry != null) {
		entry.flushWaiter(locker, mb, lockTableIndex);
	    }
	}
	return false;
  }


  /**
   * 
   * Do the real work of dumpLockTableInternal.
   */
  // line 489 "../../../../LockManager.ump"
   protected void dumpLockTableInternal(LockStats stats, int i){
    Map lockTable = lockTables[i];
	this.hook776(stats, lockTable);
	Iterator iter = lockTable.values().iterator();
	while (iter.hasNext()) {
	    Lock lock = (Lock) iter.next();
	    this.hook777(stats, lock);
	    Iterator ownerIter = lock.getOwnersClone().iterator();
	    while (ownerIter.hasNext()) {
		LockInfo info = (LockInfo) ownerIter.next();
		this.hook778(stats, info);
	    }
	}
  }


  /**
   * 
   * Debugging
   */
  // line 507 "../../../../LockManager.ump"
   public void dump() throws DatabaseException{
    System.out.println(dumpToString());
  }

  // line 511 "../../../../LockManager.ump"
   public String dumpToString() throws DatabaseException{
    StringBuffer sb = new StringBuffer();
	for (int i = 0; i < nLockTables; i++) {
	    this.hook773(sb, i);
	}
	return sb.toString();
  }

  // line 519 "../../../../LockManager.ump"
   private void dumpToStringNoLatch(StringBuffer sb, int whichTable){
    Map lockTable = lockTables[whichTable];
	Iterator entries = lockTable.entrySet().iterator();
	while (entries.hasNext()) {
	    Map.Entry entry = (Map.Entry) entries.next();
	    Long nid = (Long) entry.getKey();
	    Lock lock = (Lock) entry.getValue();
	    sb.append("---- Node Id: ").append(nid).append("----\n");
	    sb.append(lock);
	    sb.append('\n');
	}
  }

  // line 532 "../../../../LockManager.ump"
   private StringBuffer findDeadlock(Lock lock, Locker rootLocker){
    Set ownerSet = new HashSet();
	ownerSet.add(rootLocker);
	StringBuffer ret = findDeadlock1(ownerSet, lock, rootLocker);
	if (ret != null) {
	    return ret;
	} else {
	    return null;
	}
  }

  // line 543 "../../../../LockManager.ump"
   private StringBuffer findDeadlock1(Set ownerSet, Lock lock, Locker rootLocker){
    Iterator ownerIter = lock.getOwnersClone().iterator();
	while (ownerIter.hasNext()) {
	    LockInfo info = (LockInfo) ownerIter.next();
	    Locker locker = info.getLocker();
	    Lock waitsFor = locker.getWaitingFor();
	    if (ownerSet.contains(locker) || locker == rootLocker) {
		StringBuffer ret = new StringBuffer();
		ret.append("Transaction ").append(locker.toString());
		ret.append(" owns ").append(lock.getNodeId());
		ret.append(" ").append(info).append("\n");
		ret.append("Transaction ").append(locker.toString());
		ret.append(" waits for ");
		if (waitsFor == null) {
		    ret.append(" nothing");
		} else {
		    ret.append(" node ");
		    ret.append(waitsFor.getNodeId());
		}
		ret.append("\n");
		return ret;
	    }
	    if (waitsFor != null) {
		ownerSet.add(locker);
		StringBuffer sb = findDeadlock1(ownerSet, waitsFor, rootLocker);
		if (sb != null) {
		    String waitInfo = "Transaction " + locker + " waits for node " + waitsFor.getNodeId() + "\n";
		    sb.insert(0, waitInfo);
		    return sb;
		}
		ownerSet.remove(locker);
	    }
	}
	return null;
  }


  /**
   * 
   * This is just a struct to hold a multi-value return.
   */
  // line 582 "../../../../LockManager.ump"
   protected void hook770() throws DatabaseException{
    
  }

  // line 585 "../../../../LockManager.ump"
   protected void hook771(EnvironmentImpl envImpl, int i) throws DatabaseException{
    
  }

  // line 588 "../../../../LockManager.ump"
   protected void hook772(boolean nonBlockingRequest) throws DeadlockException,DatabaseException{
    
  }

  // line 591 "../../../../LockManager.ump"
   protected void hook773(StringBuffer sb, int i) throws DatabaseException{
    dumpToStringNoLatch(sb, i);
  }

  // line 595 "../../../../LockManager.ump"
   protected void hook774() throws DatabaseException{
    
  }

  // line 598 "../../../../LockManager.ump"
   protected void hook775() throws DatabaseException{
    
  }

  // line 601 "../../../../LockManager.ump"
   protected void hook776(LockStats stats, Map lockTable){
    
  }

  // line 604 "../../../../LockManager.ump"
   protected void hook777(LockStats stats, Lock lock){
    
  }

  // line 607 "../../../../LockManager.ump"
   protected void hook778(LockStats stats, LockInfo info){
    
  }

  // line 610 "../../../../LockManager.ump"
   protected void hook779(DbConfigManager configMgr) throws DatabaseException{
    nLockTables = configMgr.getInt(EnvironmentParams.N_LOCK_TABLES);
	original(configMgr);
  }

  // line 613 "../../../../LockManager.ump"
   protected void hook780(int lockTableIndex) throws DatabaseException{
    memoryBudget.updateLockMemoryUsage(TOTAL_LOCK_OVERHEAD, lockTableIndex);
	original(lockTableIndex);
  }

  // line 616 "../../../../LockManager.ump"
   protected void hook781(int lockTableIndex) throws DatabaseException{
    memoryBudget.updateLockMemoryUsage(REMOVE_TOTAL_LOCK_OVERHEAD, lockTableIndex);
	original(lockTableIndex);
  }
  /*PLEASE DO NOT EDIT THIS CODE*/
  /*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/
  
  
  
  // line 4 "../../../../LockManager_static.ump"
  public static class LockAttemptResult
  {
  
    //------------------------
    // MEMBER VARIABLES
    //------------------------
  
    //LockAttemptResult Attributes
    private boolean success;
    private Lock useLock;
    private LockGrantType lockGrant;
  
    //------------------------
    // CONSTRUCTOR
    //------------------------
  
    public LockAttemptResult(boolean aSuccess, Lock aUseLock, LockGrantType aLockGrant)
    {
      success = aSuccess;
      useLock = aUseLock;
      lockGrant = aLockGrant;
    }
  
    //------------------------
    // INTERFACE
    //------------------------
  
    public boolean setSuccess(boolean aSuccess)
    {
      boolean wasSet = false;
      success = aSuccess;
      wasSet = true;
      return wasSet;
    }
  
    public boolean setUseLock(Lock aUseLock)
    {
      boolean wasSet = false;
      useLock = aUseLock;
      wasSet = true;
      return wasSet;
    }
  
    public boolean setLockGrant(LockGrantType aLockGrant)
    {
      boolean wasSet = false;
      lockGrant = aLockGrant;
      wasSet = true;
      return wasSet;
    }
  
    public boolean getSuccess()
    {
      return success;
    }
  
    public Lock getUseLock()
    {
      return useLock;
    }
  
    public LockGrantType getLockGrant()
    {
      return lockGrant;
    }
  
    public void delete()
    {}
  
    // line 9 "../../../../LockManager_static.ump"
    public  LockAttemptResult(Lock useLock, LockGrantType lockGrant, boolean success){
      this.useLock=useLock;
          this.lockGrant=lockGrant;
          this.success=success;
    }
  
  
    public String toString()
    {
      return super.toString() + "["+
              "success" + ":" + getSuccess()+ "]" + System.getProperties().getProperty("line.separator") +
              "  " + "useLock" + "=" + (getUseLock() != null ? !getUseLock().equals(this)  ? getUseLock().toString().replaceAll("  ","    ") : "this" : "null") + System.getProperties().getProperty("line.separator") +
              "  " + "lockGrant" + "=" + (getLockGrant() != null ? !getLockGrant().equals(this)  ? getLockGrant().toString().replaceAll("  ","    ") : "this" : "null");
    }
  }  
  //------------------------
  // DEVELOPER CODE - PROVIDED AS-IS
  //------------------------
  
  // line 23 "../../../../LockManager.ump"
  protected int nLockTables = 1 ;
// line 25 "../../../../LockManager.ump"
  private Map[] lockTables ;
// line 27 "../../../../LockManager.ump"
  private EnvironmentImpl envImpl ;
// line 29 "../../../../LockManager.ump"
  private MemoryBudget memoryBudget ;
// line 31 "../../../../LockManager.ump"
  private static RangeRestartException rangeRestartException = new RangeRestartException() ;
// line 33 "../../../../LockManager.ump"
  private static boolean lockTableDump = false ;
// line 150 "../../../../LockManager.ump"
  abstract protected LockAttemptResult attemptLock(Long nodeId, Locker locker, LockType type,
	    boolean nonBlockingRequest) throws DatabaseException ;
// line 463 "../../../../LockManager.ump"
  abstract protected boolean validateOwnership(Long nodeId, Locker locker, LockType type, boolean flushFromWaiters,
	    MemoryBudget mb) throws DatabaseException ;
// line 483 "../../../../LockManager.ump"
  abstract protected void dumpLockTable(LockStats stats) throws DatabaseException ;
// line 5 "../../../../MemoryBudget_LockManager.ump"
  static final long TOTAL_LOCK_OVERHEAD = MemoryBudget.LOCK_OVERHEAD + MemoryBudget.HASHMAP_ENTRY_OVERHEAD
	    + MemoryBudget.LONG_OVERHEAD ;
// line 8 "../../../../MemoryBudget_LockManager.ump"
  private static final long REMOVE_TOTAL_LOCK_OVERHEAD = 0 - TOTAL_LOCK_OVERHEAD ;

  
}