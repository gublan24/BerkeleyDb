/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Sleepycat Software.  All rights reserved.
 *
 * $Id: TreeWalkerStatsAccumulator.java,v 1.3 2006/01/03 21:55:58 bostic Exp $
 */

package com.sleepycat.je.tree;

/**
 * Accumulates stats about a tree during tree walking.
 */
public interface TreeWalkerStatsAccumulator {
    public void processIN(IN node, Long nid, int level);

    public void processBIN(BIN node, Long nid, int level);

    public void processDIN(DIN node, Long nid, int level);

    public void processDBIN(DBIN node, Long nid, int level);

    public void processDupCountLN(DupCountLN node, Long nid);

    public void incrementLNCount();

    public void incrementDeletedLNCount();
}
