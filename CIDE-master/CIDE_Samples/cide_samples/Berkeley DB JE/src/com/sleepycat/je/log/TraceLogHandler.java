/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Sleepycat Software.  All rights reserved.
 *
 * $Id: TraceLogHandler.java,v 1.1 2006/05/06 09:00:03 ckaestne Exp $
 */

package com.sleepycat.je.log;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.utilint.Tracer;

/**
 * Handler for java.util.logging. Takes logging records and publishes them into
 * the database log.
 */
public class TraceLogHandler extends Handler {

    private EnvironmentImpl env;

    public TraceLogHandler(EnvironmentImpl env) {
        this.env = env;
    }

    public void close() {
    }

    public void flush() {
    }

    public void publish(LogRecord l) {
        if (!env.isReadOnly() &&
	    !env.mayNotWrite()) {
            try {
                Tracer newRec = new Tracer(l.getMessage());
                env.getLogManager().log(newRec);
            } catch (DatabaseException e) {
                /* Eat exception. */
                System.err.println("Problem seen while tracing into " +
                                   "the database log:");
                e.printStackTrace();
            }
        }
    }
}
