/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Sleepycat Software.  All rights reserved.
 *
 * $Id: TestUtilLogReader.java,v 1.3 2006/01/03 21:56:23 bostic Exp $
 */

package com.sleepycat.je.log;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.log.entry.LogEntry;
import com.sleepycat.je.utilint.DbLsn;

/**
 * Instantiates all log entries using the shared log entry instances.
 */
public class TestUtilLogReader extends FileReader {

    private LogEntryType entryType;
    private LogEntry entry;

    public TestUtilLogReader(EnvironmentImpl env)
	throws IOException, DatabaseException {

        super(env,
              4096,
              true,
              DbLsn.NULL_LSN,
              null,
              DbLsn.NULL_LSN,
              DbLsn.NULL_LSN);
    }

    public TestUtilLogReader(EnvironmentImpl env,
                             int readBufferSize,
                             boolean forward,
                             long startLsn,
                             Long singleFileNumber,
                             long endOfFileLsn,
                             long finishLsn)
	throws IOException, DatabaseException {

        super(env,
              readBufferSize,
              forward,
              startLsn,
              singleFileNumber,
              endOfFileLsn,
              finishLsn);
    }

    public LogEntryType getEntryType() {
        return entryType;
    }

    public LogEntry getEntry() {
        return entry;
    }

    protected boolean isTargetEntry(byte logEntryTypeNumber,
                                    byte logEntryTypeVersion) {
        return true;
    }

    protected boolean processEntry(ByteBuffer entryBuffer)
        throws DatabaseException {

        entryType = LogEntryType.findType
            (currentEntryTypeNum, currentEntryTypeVersion);
	entry = entryType.getSharedLogEntry();
        entry.readEntry
            (entryBuffer, currentEntrySize, currentEntryTypeVersion, true);
        return true;
    }
}
