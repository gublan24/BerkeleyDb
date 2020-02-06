/*PLEASE DO NOT EDIT THIS CODE*/
/*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/

package com.sleepycat.je.tree;
import de.ovgu.cide.jakutil.*;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.log.LogWritable;
import com.sleepycat.je.log.LogUtils;
import com.sleepycat.je.log.LogReadable;
import com.sleepycat.je.log.LogException;
import java.nio.ByteBuffer;
import com.sleepycat.je.log.*;

// line 3 "../../../../DeltaInfo.ump"
public class DeltaInfo implements LogWritable,LogReadable
{

  //------------------------
  // MEMBER VARIABLES
  //------------------------

  //DeltaInfo Attributes
  private long lsn;

  //------------------------
  // CONSTRUCTOR
  //------------------------

  public DeltaInfo()
  {
    lsn = DbLsn.NULL_LSN;
  }

  //------------------------
  // INTERFACE
  //------------------------

  public boolean setLsn(long aLsn)
  {
    boolean wasSet = false;
    lsn = aLsn;
    wasSet = true;
    return wasSet;
  }

  public long getLsn()
  {
    return lsn;
  }

  public void delete()
  {}

  // line 20 "../../../../DeltaInfo.ump"
  public  DeltaInfo(byte [] key, long lsn, byte state){
    this.key = key;
	this.lsn = lsn;
	this.state = state;
  }


  /**
   * 
   * For reading from the log only.
   * DeltaInfo() {	lsn = DbLsn.NULL_LSN; }
   */
  // line 31 "../../../../DeltaInfo.ump"
   public int getLogSize(){
    return LogUtils.getByteArrayLogSize(key) + LogUtils.getLongLogSize() + 1;
  }

  // line 35 "../../../../DeltaInfo.ump"
   public void writeToLog(ByteBuffer logBuffer){
    LogUtils.writeByteArray(logBuffer, key);
	LogUtils.writeLong(logBuffer, lsn);
	logBuffer.put(state);
  }

  // line 41 "../../../../DeltaInfo.ump"
   public void readFromLog(ByteBuffer itemBuffer, byte entryTypeVersion) throws LogException{
    key = LogUtils.readByteArray(itemBuffer);
	lsn = LogUtils.readLong(itemBuffer);
	state = itemBuffer.get();
  }

  // line 47 "../../../../DeltaInfo.ump"
   public void dumpLog(StringBuffer sb, boolean verbose){
    sb.append(Key.dumpString(key, 0));
	sb.append(DbLsn.toString(lsn));
	IN.dumpDeletedState(sb, state);
  }


  /**
   * 
   * @see LogReadable#logEntryIsTransactional
   */
  // line 56 "../../../../DeltaInfo.ump"
   public boolean logEntryIsTransactional(){
    return false;
  }


  /**
   * 
   * @see LogReadable#getTransactionId
   */
  // line 63 "../../../../DeltaInfo.ump"
   public long getTransactionId(){
    return 0;
  }


  /**
   * 
   * @return the Key.
   */
  // line 70 "../../../../DeltaInfo.ump"
  public byte[] getKey(){
    return key;
  }


  /**
   * 
   * @return the state flags.
   */
  // line 77 "../../../../DeltaInfo.ump"
  public byte getState(){
    return state;
  }


  /**
   * 
   * @return true if this is known to be deleted.
   */
  // line 84 "../../../../DeltaInfo.ump"
  public boolean isKnownDeleted(){
    return IN.isStateKnownDeleted(state);
  }


  public String toString()
  {
    return super.toString() + "["+
            "lsn" + ":" + getLsn()+ "]";
  }  
  //------------------------
  // DEVELOPER CODE - PROVIDED AS-IS
  //------------------------
  
  // line 13 "../../../../DeltaInfo.ump"
  private byte[] key ;
// line 17 "../../../../DeltaInfo.ump"
  private byte state ;

  
}