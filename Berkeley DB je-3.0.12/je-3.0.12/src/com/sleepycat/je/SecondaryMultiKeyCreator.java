/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Sleepycat Software.  All rights reserved.
 *
 * $Id: SecondaryMultiKeyCreator.java,v 1.2 2006/01/03 21:55:37 bostic Exp $
 */

package com.sleepycat.je;

import java.util.Set;

/**
 * Javadoc for this public method is generated via
 * the doc templates in the doc_src directory.
 */
public interface SecondaryMultiKeyCreator {

    /**
     * Javadoc for this public method is generated via
     * the doc templates in the doc_src directory.
     */
    public void createSecondaryKeys(SecondaryDatabase secondary,
				    DatabaseEntry key,
				    DatabaseEntry data,
				    Set results)
	throws DatabaseException;
}
