/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Sleepycat Software.  All rights reserved.
 *
 * $Id: LogManager.java,v 1.1 2006/05/06 09:00:02 ckaestne Exp $
 */

package com.sleepycat.je.log;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.zip.Checksum;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentStats;
import com.sleepycat.je.RunRecoveryException;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.cleaner.TrackedFileSummary;
import com.sleepycat.je.cleaner.UtilizationTracker;
import com.sleepycat.je.config.EnvironmentParams;
import com.sleepycat.je.dbi.DbConfigManager;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.latch.Latch;
import com.sleepycat.je.latch.LatchSupport;
import com.sleepycat.je.log.entry.LogEntry;
import com.sleepycat.je.utilint.Adler32;
import com.sleepycat.je.utilint.DbLsn;
import com.sleepycat.je.utilint.TestHook;
import com.sleepycat.je.utilint.Tracer;

/**
 * The LogManager supports reading and writing to the JE log.
 */
abstract public class LogManager {

    // no-op loggable object
    private static final String DEBUG_NAME = LogManager.class.getName();
    
    /*
     * Log entry header field sizes
     */
    static final int HEADER_BYTES = 14;            // size of entry header
    static final int CHECKSUM_BYTES = 4;           // size of checksum field
    static final int PREV_BYTES = 4;               // size of previous field
    static final int HEADER_CONTENT_BYTES =
        HEADER_BYTES - CHECKSUM_BYTES;
    static final int HEADER_CHECKSUM_OFFSET = 0;
    static final int HEADER_ENTRY_TYPE_OFFSET = 4;
    static final int HEADER_VERSION_OFFSET = 5;
    static final int HEADER_PREV_OFFSET = 6;
    static final int HEADER_SIZE_OFFSET = 6+4;

    protected LogBufferPool logBufferPool; // log buffers
    protected Latch logWriteLatch;           // synchronizes log writes
    private boolean doChecksumOnRead;      // if true, do checksum on read
    private FileManager fileManager;       // access to files
    private CheckpointMonitor checkpointMonitor;
    protected EnvironmentImpl envImpl;
    private boolean readOnly;
    private int readBufferSize; // how many bytes to read when faulting in.
    /* The last LSN in the log during recovery. */
    private long lastLsnAtRecovery = DbLsn.NULL_LSN;

    /* Stats */

    /* 
     * Number of times we have to repeat a read when we fault in an object
     * because the initial read was too small.    
     */
    private int nRepeatFaultReads; 

    /* 
     * Number of times we have to use the temporary marshalling buffer to
     * write to the log.
     */
    private long nTempBufferWrites;

    /* For unit tests */
    private TestHook readHook; // used for generating exceptions on log reads

    /**
     * There is a single log manager per database environment.
     */
    public LogManager(EnvironmentImpl envImpl,
                      boolean readOnly)
        throws DatabaseException {

        // Set up log buffers
        this.envImpl = envImpl;
        this.fileManager = envImpl.getFileManager();
        DbConfigManager configManager = envImpl.getConfigManager();
	this.readOnly = readOnly;
        logBufferPool = new LogBufferPool(fileManager, envImpl);

        /* See if we're configured to do a checksum when reading in objects. */
        doChecksumOnRead =
	    configManager.getBoolean(EnvironmentParams.LOG_CHECKSUM_READ);

        logWriteLatch = LatchSupport.makeLatch(DEBUG_NAME, envImpl);
        readBufferSize =
	    configManager.getInt(EnvironmentParams.LOG_FAULT_READ_SIZE);
        checkpointMonitor = new CheckpointMonitor(envImpl);
    }

    public boolean getChecksumOnRead() {
        return doChecksumOnRead;
    }

    public long getLastLsnAtRecovery() {
	return lastLsnAtRecovery;
    }

    public void setLastLsnAtRecovery(long lastLsnAtRecovery) {
	this.lastLsnAtRecovery = lastLsnAtRecovery;
    }

    /**
     * Reset the pool when the cache is resized.  This method is called after
     * the memory budget has been calculated.
     */
    public void resetPool(DbConfigManager configManager)
	throws DatabaseException {

        logBufferPool.reset(configManager);
    }

    /*
     * Writing to the log
     */

    /**
     * Log this single object and force a write of the log files.
     * @param item object to be logged
     * @param fsyncRequired if true, log files should also be fsynced.
     * @return LSN of the new log entry
     */
    public long logForceFlush(LoggableObject item,
                              boolean fsyncRequired)
	throws DatabaseException {

        return log(item,
                   false, // is provisional
                   true,  // flush required
                   fsyncRequired,
		   false, // forceNewLogFile
                   DbLsn.NULL_LSN); // oldNodeLsn, for obsolete counting.
    }

    /**
     * Log this single object and force a flip of the log files.
     * @param item object to be logged
     * @param fsyncRequired if true, log files should also be fsynced.
     * @return LSN of the new log entry
     */
    public long logForceFlip(LoggableObject item)
	throws DatabaseException {

        return log(item,
                   false, // is provisional
                   true,  // flush required
                   false, // fsync required
		   true,  // forceNewLogFile
                   DbLsn.NULL_LSN); // oldNodeLsn, for obsolete counting.
    }

    /**
     * Write a log entry.
     * @return LSN of the new log entry
     */
    public long log(LoggableObject item) 
	throws DatabaseException {

        return log(item,
                   false,           // is provisional
                   false,           // flush required
                   false,           // fsync required
		   false,           // forceNewLogFile
                   DbLsn.NULL_LSN); // old lsn
    }

    /**
     * Write a log entry.
     * @return LSN of the new log entry
     */
    public long log(LoggableObject item,
		    boolean isProvisional,
		    long oldNodeLsn)
	throws DatabaseException {

        return log(item,
                   isProvisional,
                   false, // flush required
                   false, // fsync required
		   false, // forceNewLogFile
                   oldNodeLsn);
    }

    /**
     * Write a log entry.
     * @param item is the item to be logged.
     * @param isProvisional true if this entry should not be read during
     * recovery.
     * @param flushRequired if true, write the log to the file after
     * adding the item. i.e. call java.nio.channel.FileChannel.write().
     * @param fsyncRequired if true, fsync the last file after adding the item.
     * @param oldNodeLsn is the previous version of the node to be counted as
     * obsolete, or null if the item is not a node or has no old LSN.
     * @return LSN of the new log entry
     */
    private long log(LoggableObject item,
                     boolean isProvisional,
                     boolean flushRequired,
                     boolean fsyncRequired,
		     boolean forceNewLogFile,
                     long oldNodeLsn)
	throws DatabaseException {

	if (readOnly) {
	    return DbLsn.NULL_LSN;
	}

        boolean marshallOutsideLatch = item.marshallOutsideWriteLatch();
        ByteBuffer marshalledBuffer = null;
        UtilizationTracker tracker = envImpl.getUtilizationTracker();
        LogResult logResult = null;

        try {

            /* 
             * If possible, marshall this item outside the log write
             * latch to allow greater concurrency by shortening the
             * write critical section.
             */
            if (marshallOutsideLatch) {
                int itemSize = item.getLogSize();
                int entrySize = itemSize + HEADER_BYTES;
		marshalledBuffer = marshallIntoBuffer(item,
                                                      itemSize,
                                                      isProvisional,
                                                      entrySize);
            }

            logResult = logItem(item, isProvisional, flushRequired,
                                forceNewLogFile, oldNodeLsn,
                                marshallOutsideLatch, marshalledBuffer,
                                tracker);

        } catch (BufferOverflowException e) {

            /* 
             * A BufferOverflowException may be seen when a thread is
             * interrupted in the middle of the log and the nio direct buffer
             * is mangled is some way by the NIO libraries. JE applications
             * should refrain from using thread interrupt as a thread
             * communications mechanism because nio behavior in the face of
             * interrupts is uncertain. See SR [#10463].
             *
             * One way or another, this type of io exception leaves us in an
             * unworkable state, so throw a run recovery exception.
             */
            throw new RunRecoveryException(envImpl, e);
        } catch (IOException e) {

            /*
             * Other IOExceptions, such as out of disk conditions, should
             * notify the application but leave the environment in workable
             * condition.
             */
            throw new DatabaseException(Tracer.getStackTrace(e), e);
        }

        /*
         * Finish up business outside of the log write latch critical section.
         */

        /* 
	 * If this logged object needs to be fsynced, do so now using the group
	 * commit mechanism.
         */
        if (fsyncRequired) {
            fileManager.groupSync();
        }

        /* 
         * Periodically, as a function of how much data is written, ask the
	 * checkpointer or the cleaner to wake up.
         */
        if (logResult.wakeupCheckpointer) {
            checkpointMonitor.activate();
        }
        if (logResult.wakeupCleaner) {
            tracker.activateCleaner();
        }

        return logResult.currentLsn;
    }

    abstract protected LogResult logItem(LoggableObject item,
                                         boolean isProvisional,
                                         boolean flushRequired,
					 boolean forceNewLogFile,
                                         long oldNodeLsn,
                                         boolean marshallOutsideLatch,
                                         ByteBuffer marshalledBuffer,
                                         UtilizationTracker tracker)
        throws IOException, DatabaseException;

    /**
     * Called within the log write critical section. 
     */
    protected LogResult logInternal(LoggableObject item,
                                    boolean isProvisional,
                                    boolean flushRequired,
				    boolean forceNewLogFile,
                                    long oldNodeLsn,
                                    boolean marshallOutsideLatch,
                                    ByteBuffer marshalledBuffer,
                                    UtilizationTracker tracker)
        throws IOException, DatabaseException {

        /* 
         * Do obsolete tracking before marshalling a FileSummaryLN into the log
         * buffer so that a FileSummaryLN counts itself.  countObsoleteNode
         * must be called before computing the entry size, since it can change
         * the size of a FileSummaryLN entry that we're logging
         */
        LogEntryType entryType = item.getLogType();
        if (oldNodeLsn != DbLsn.NULL_LSN) {
            tracker.countObsoleteNode(oldNodeLsn, entryType);
        }

        /*
         * If an item must be protected within the log write latch for 
         * marshalling, take care to also calculate its size in the protected 
         * section. Note that we have to get the size *before* marshalling so
         * that the currentLsn and size are correct for utilization tracking.
         */
        int entrySize;
        if (marshallOutsideLatch) {
            entrySize = marshalledBuffer.limit();
        } else {
            entrySize = item.getLogSize() + HEADER_BYTES;
        }

        /* 
         * Get the next free slot in the log, under the log write latch.  Bump
         * the LSN values, which gives us a valid previous pointer, which is
         * part of the log entry header. That's why doing the checksum must be
         * in the log write latch -- we need to bump the LSN first, and bumping
         * the LSN must be done within the log write latch.
         */

	if (forceNewLogFile) {
	    fileManager.forceNewLogFile();
	}

        boolean flippedFile = fileManager.bumpLsn(entrySize);
        long currentLsn = DbLsn.NULL_LSN;
        boolean wakeupCleaner = false;
	boolean usedTemporaryBuffer = false;
        try {
            currentLsn = fileManager.getLastUsedLsn();
            
            /* 
             * countNewLogEntry and countObsoleteNodeInexact cannot change a
             * FileSummaryLN size, so they are safe to call after getLogSize().
             */
            wakeupCleaner =
                tracker.countNewLogEntry(currentLsn, entryType, entrySize);

            /*
             * LN deletions are obsolete immediately.  Inexact counting is used
             * to save resources because the cleaner knows that all deleted LNs
             * are obsolete.
             */
            if (item.countAsObsoleteWhenLogged()) {
                tracker.countObsoleteNodeInexact(currentLsn, entryType);
            }

            /* 
             * This item must be marshalled within the log write latch.
             */
            if (!marshallOutsideLatch) {
                marshalledBuffer = marshallIntoBuffer(item,
                                                      entrySize-HEADER_BYTES,
                                                      isProvisional,
                                                      entrySize);
            }

            /* Sanity check */
            if (entrySize != marshalledBuffer.limit()) {
                throw new DatabaseException(
                 "Logged item entrySize= " + entrySize +
                 " but marshalledSize=" + marshalledBuffer.limit() +
                 " type=" + entryType + " currentLsn=" +
                 DbLsn.getNoFormatString(currentLsn));
            }
                                            
            /*
             * Ask for a log buffer suitable for holding this new entry.  If
             * the current log buffer is full, or if we flipped into a new
             * file, write it to disk and get a new, empty log buffer to
             * use. The returned buffer will be latched for write.
             */
            LogBuffer useLogBuffer =
                logBufferPool.getWriteBuffer(entrySize, flippedFile);

            /* Add checksum to entry. */
            marshalledBuffer =
                addPrevOffsetAndChecksum(marshalledBuffer,
                                         fileManager.getPrevEntryOffset(),
                                         entrySize);

	    /*
	     * If the LogBufferPool buffer (useBuffer) doesn't have sufficient
	     * space (since they're fixed size), just use the temporary buffer
	     * and throw it away when we're done.  That way we don't grow the
	     * LogBuffers in the pool permanently.  We risk an OOME on this
	     * temporary usage, but we'll risk it.  [#12674]
	     */
            useLogBuffer.latchForWrite();
            try {
                ByteBuffer useBuffer = useLogBuffer.getDataBuffer();
                if (useBuffer.capacity() - useBuffer.position() < entrySize) {
                    fileManager.writeLogBuffer
                        (new LogBuffer(marshalledBuffer, currentLsn));
                    usedTemporaryBuffer = true;
                    assert useBuffer.position() == 0;
                    nTempBufferWrites++;
                } else {
                    /* Copy marshalled object into write buffer. */
                    useBuffer.put(marshalledBuffer);
                }
            } finally {
                useLogBuffer.release();
            }
        } catch (Exception e) {

            /* 
             * The LSN pointer, log buffer position, and corresponding file
             * position march in lockstep.
	     *
             * 1. We bump the LSN.
             * 2. We copy loggable item into the log buffer.
             * 3. We may try to write the log buffer.
             * 
             * If we've failed to put the item into the log buffer (2), we need
             * to restore old LSN state so that the log buffer doesn't have a
             * hole. [SR #12638] If we fail after (2), we don't need to restore
             * state, because log buffers will still match file positions.
             */
            fileManager.restoreLastPosition();
            if (e instanceof DatabaseException) {
                throw (DatabaseException) e;
            } else if (e instanceof IOException){
                throw (IOException) e;
            } else {
                throw new DatabaseException(e);
            }
        }
        
	/* 
	 * Tell the log buffer pool that we finished the write.  Record the
	 * LSN against this logbuffer, and write the buffer to disk if
	 * needed.
	 */
	if (!usedTemporaryBuffer) {
	    logBufferPool.writeCompleted(currentLsn, flushRequired);
	}

        /*
         * If the txn is not null, the first item is an LN. Update the txn with
         * info about the latest LSN. Note that this has to happen within the
         * log write latch.
         */
        item.postLogWork(currentLsn);

        boolean wakeupCheckpointer =
            checkpointMonitor.recordLogWrite(entrySize, item);

        return new LogResult(currentLsn, wakeupCheckpointer, wakeupCleaner);
    }

    /**
     * Serialize a loggable object into this buffer.
     */
    private ByteBuffer marshallIntoBuffer(LoggableObject item,
                                          int itemSize,
                                          boolean isProvisional,
                                          int entrySize)
	throws DatabaseException {

        ByteBuffer destBuffer = ByteBuffer.allocate(entrySize);

        /* Reserve 4 bytes at the head for the checksum. */
        destBuffer.position(CHECKSUM_BYTES);

        /* Write the header. */
        writeHeader(destBuffer, item.getLogType(), itemSize, isProvisional);

        /* Put the entry in. */
        item.writeToLog(destBuffer);

        /* Set the limit so it can be used as the size of the entry. */
        destBuffer.flip();

        return destBuffer;
    }

    private ByteBuffer addPrevOffsetAndChecksum(ByteBuffer destBuffer,
                                                long lastOffset,
                                                int entrySize) {

        Checksum checksum = Adler32.makeChecksum();
            
        /* Add the prev pointer */
        destBuffer.position(HEADER_PREV_OFFSET);
        LogUtils.writeUnsignedInt(destBuffer, lastOffset);

        /* Now calculate the checksum and write it into the buffer. */
        checksum.update(destBuffer.array(), CHECKSUM_BYTES,
                        (entrySize - CHECKSUM_BYTES));
        destBuffer.position(0);
        LogUtils.writeUnsignedInt(destBuffer, checksum.getValue());

        /* Leave this buffer ready for copying into another buffer. */
        destBuffer.position(0);

        return destBuffer;
    }

    /**
     * Serialize a loggable object into this buffer. Return it ready for a
     * copy.
     */
    ByteBuffer putIntoBuffer(LoggableObject item,
                             int itemSize,
                             long prevLogEntryOffset,
                             boolean isProvisional,
                             int entrySize)
	throws DatabaseException {

        ByteBuffer destBuffer =
	    marshallIntoBuffer(item, itemSize, isProvisional, entrySize);
        return addPrevOffsetAndChecksum(destBuffer, 0, entrySize);
    }

    /**
     * Helper to write the common entry header.
     * @param destBuffer destination
     * @param item object being logged
     * @param itemSize We could ask the item for this, but are passing it
     * as a parameter for efficiency, because it's already available
     */
    private void writeHeader(ByteBuffer destBuffer,
                             LogEntryType itemType,
                             int itemSize,
                             boolean isProvisional) {
        // log entry type
        byte typeNum = itemType.getTypeNum();
        destBuffer.put(typeNum);

        // version
        byte version = itemType.getVersion();
        if (isProvisional)
            version = LogEntryType.setProvisional(version);
        destBuffer.put(version);

        // entry size
        destBuffer.position(HEADER_SIZE_OFFSET);
        LogUtils.writeInt(destBuffer, itemSize);
    }

    /*
     * Reading from the log.
     */

    /**
     * Instantiate all the objects in the log entry at this LSN.
     * @param lsn location of entry in log.
     * @return log entry that embodies all the objects in the log entry.
     */
    public LogEntry getLogEntry(long lsn) 
        throws DatabaseException {

	/*
	 * Fail loudly if the environment is invalid.  A RunRecoveryException
	 * must have occurred.
	 */
	envImpl.checkIfInvalid();

        /*
         * Get a log source for the log entry which provides an abstraction
         * that hides whether the entry is in a buffer or on disk. Will
         * register as a reader for the buffer or the file, which will take a
         * latch if necessary.
         */
        LogSource logSource = getLogSource(lsn);

        /* Read the log entry from the log source. */
        return getLogEntryFromLogSource(lsn, logSource);
    }

    LogEntry getLogEntry(long lsn, RandomAccessFile file)
        throws DatabaseException {

        return getLogEntryFromLogSource
	    (lsn, new FileSource(file, readBufferSize, fileManager));
    }

    /**
     * Instantiate all the objects in the log entry at this LSN. This will
     * release the log source at the first opportunity.
     *
     * @param lsn location of entry in log
     * @return log entry that embodies all the objects in the log entry
     */
    private LogEntry getLogEntryFromLogSource(long lsn,
                                              LogSource logSource) 
        throws DatabaseException {

        try {

            /*
             * Read the log entry header into a byte buffer. Be sure to read it
             * in the order that it was written, and with the same marshalling!
             * Ideally, entry header read/write would be encapsulated in a
             * single class, but we don't want to have to instantiate a new
             * object in the critical path here.
	     * XXX - false economy, change.
             */
            long fileOffset = DbLsn.getFileOffset(lsn);
            ByteBuffer entryBuffer = logSource.getBytes(fileOffset);

            /* Read the checksum to move the buffer forward. */
            ChecksumValidator validator = null;
            long storedChecksum = LogUtils.getUnsignedInt(entryBuffer);
            if (doChecksumOnRead) {
                validator = new ChecksumValidator();
                validator.update(envImpl, entryBuffer,
				 HEADER_CONTENT_BYTES, false);
            }

            /* Read the header. */
            byte loggableType = entryBuffer.get(); // log entry type
            byte version = entryBuffer.get();      // version
            /* Read the size, skipping over the prev offset. */
            entryBuffer.position(entryBuffer.position() + PREV_BYTES);
            int itemSize = LogUtils.readInt(entryBuffer);

            /*
             * Now that we know the size, read the rest of the entry
             * if the first read didn't get enough.
             */
            if (entryBuffer.remaining() < itemSize) {
                entryBuffer = logSource.getBytes(fileOffset + HEADER_BYTES,
                                                 itemSize);
                nRepeatFaultReads++;
            }

            /*
             * Do entry validation. Run checksum before checking the entry
             * type, it will be the more encompassing error.
             */
            if (doChecksumOnRead) {
                /* Check the checksum first. */
                validator.update(envImpl, entryBuffer, itemSize, false);
                validator.validate(envImpl, storedChecksum, lsn);
            }

            assert LogEntryType.isValidType(loggableType):
                "Read non-valid log entry type: " + loggableType;

            /* Read the entry. */
            LogEntry logEntry = 
                LogEntryType.findType(loggableType, version).getNewLogEntry();
            logEntry.readEntry(entryBuffer, itemSize, version, true);

            /* For testing only; generate a read io exception. */
            if (readHook != null) {
                readHook.doIOHook();
            }

            /* 
             * Done with the log source, release in the finally clause.  Note
             * that the buffer we get back from logSource is just a duplicated
             * buffer, where the position and state are copied but not the
             * actual data. So we must not release the logSource until we are
             * done marshalling the data from the buffer into the object
             * itself.
             */
            return logEntry;
        } catch (DatabaseException e) {

            /* 
	     * Propagate DatabaseExceptions, we want to preserve any subtypes
             * for downstream handling.
             */
            throw e;
        } catch (ClosedChannelException e) {

            /* 
             * The channel should never be closed. It may be closed because
             * of an interrupt received by another thread. See SR [#10463]
             */
            throw new RunRecoveryException(envImpl,
                                           "Channel closed, may be "+
                                           "due to thread interrupt",
                                           e);
        } catch (Exception e) {
            throw new DatabaseException(e);
        } finally {
            if (logSource != null) {
                logSource.release();
            }
        }
    }

    /**
     * Fault in the first object in the log entry log entry at this LSN.
     * @param lsn location of object in log
     * @return the object in the log
     */
    public Object get(long lsn)
        throws DatabaseException {

        LogEntry entry = getLogEntry(lsn);
        return entry.getMainItem();
    }

    /**
     * Find the LSN, whether in a file or still in the log buffers.
     */
    private LogSource getLogSource(long lsn)
        throws DatabaseException {

        /*
	 * First look in log to see if this LSN is still in memory.
	 */
        LogBuffer logBuffer = logBufferPool.getReadBuffer(lsn);

        if (logBuffer == null) {
            try {
                /* Not in the in-memory log -- read it off disk. */
                return new FileHandleSource
                    (fileManager.getFileHandle(DbLsn.getFileNumber(lsn)),
                     readBufferSize,
		     fileManager);
            } catch (LogFileNotFoundException e) {
                /* Add LSN to exception message. */
                throw new LogFileNotFoundException
		    (DbLsn.getNoFormatString(lsn) + ' ' + e.getMessage());
            }
        } else {
            return logBuffer;
        }
    }

    /**
     * Flush all log entries, fsync the log file.
     */
    public void flush()
	throws DatabaseException {

	if (readOnly) {
	    return;
	}

        flushInternal();
        fileManager.syncLogEnd();
    }

    abstract protected void flushInternal()
        throws LogException, DatabaseException;


    public void loadStats(StatsConfig config, EnvironmentStats stats) 
        throws DatabaseException {

        stats.setNRepeatFaultReads(nRepeatFaultReads);
	stats.setNTempBufferWrites(nTempBufferWrites);
        if (config.getClear()) {
            nRepeatFaultReads = 0;
            nTempBufferWrites = 0;
        }

        logBufferPool.loadStats(config, stats);
        fileManager.loadStats(config, stats);
    }

    /**
     * Returns a tracked summary for the given file which will not be flushed.
     * Used for watching changes that occur while a file is being cleaned.
     */
    abstract public TrackedFileSummary getUnflushableTrackedSummary(long file)
        throws DatabaseException;

    protected TrackedFileSummary getUnflushableTrackedSummaryInternal(long file)
        throws DatabaseException {

        return envImpl.getUtilizationTracker().
                       getUnflushableTrackedSummary(file);
    }

    /**
     * Count node as obsolete under the log write latch.  This is done here
     * because the log write latch is managed here, and all utilization
     * counting must be performed under the log write latch.
     */
    abstract public void countObsoleteNode(long lsn, LogEntryType type)
        throws DatabaseException;

    protected void countObsoleteNodeInternal(UtilizationTracker tracker,
                                             long lsn,
                                             LogEntryType type)
        throws DatabaseException {
        
        tracker.countObsoleteNode(lsn, type);
    }

    /**
     * Counts file summary info under the log write latch.
     */
    abstract public void countObsoleteNodes(TrackedFileSummary[] summaries)
        throws DatabaseException;

    protected void countObsoleteNodesInternal(UtilizationTracker tracker,
                                              TrackedFileSummary[] summaries)
        throws DatabaseException {
        
        for (int i = 0; i < summaries.length; i += 1) {
            TrackedFileSummary summary = summaries[i];
            tracker.addSummary(summary.getFileNumber(), summary);
        }
    }

    /**
     * Counts the given obsolete IN LSNs under the log write latch.
     */
    abstract public void countObsoleteINs(List lsnList)
        throws DatabaseException;

    protected void countObsoleteINsInternal(List lsnList)
        throws DatabaseException {
        
        UtilizationTracker tracker = envImpl.getUtilizationTracker();

        for (int i = 0; i < lsnList.size(); i += 1) {
            Long offset = (Long) lsnList.get(i);
            tracker.countObsoleteNode(offset.longValue(), LogEntryType.LOG_IN);
        }
    }

    /* For unit testing only. */
    public void setReadHook(TestHook hook) {
        readHook = hook;
    }

    /** 
     * LogResult holds the multivalue return from logInternal.
     */
    static class LogResult {
        long currentLsn;
        boolean wakeupCheckpointer;
        boolean wakeupCleaner;

        LogResult(long currentLsn,
                  boolean wakeupCheckpointer,
                  boolean wakeupCleaner) {
            this.currentLsn = currentLsn;
            this.wakeupCheckpointer = wakeupCheckpointer;
            this.wakeupCleaner = wakeupCleaner;
        }
    }
}
