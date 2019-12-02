/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Sleepycat Software.  All rights reserved.
 *
 * $Id: KeyTest.java,v 1.13 2006/01/03 21:56:30 bostic Exp $
 */

package com.sleepycat.je.tree;

import junit.framework.TestCase;

public class KeyTest extends TestCase {
    public void setUp() {
    }

    public void tearDown() {
    }

    public void testKeyComparisonPerformance() {
	byte[] key1 = "abcdefghijabcdefghij".getBytes();
	byte[] key2 = "abcdefghijabcdefghij".getBytes();

	for (int i = 0; i < 1000000; i++) {
	    assertTrue(Key.compareKeys(key1, key2, null) == 0);
	}
    }

    public void testKeyComparison() {
	byte[] key1 = "aaa".getBytes();
	byte[] key2 = "aab".getBytes();
	assertTrue(Key.compareKeys(key1, key2, null) < 0);
	assertTrue(Key.compareKeys(key2, key1, null) > 0);
	assertTrue(Key.compareKeys(key1, key1, null) == 0);

	key1 = "aa".getBytes();
	key2 = "aab".getBytes();
	assertTrue(Key.compareKeys(key1, key2, null) < 0);
	assertTrue(Key.compareKeys(key2, key1, null) > 0);

	key1 = "".getBytes();
	key2 = "aab".getBytes();
	assertTrue(Key.compareKeys(key1, key2, null) < 0);
	assertTrue(Key.compareKeys(key2, key1, null) > 0);
	assertTrue(Key.compareKeys(key1, key1, null) == 0);

	key1 = "".getBytes();
	key2 = "".getBytes();
	assertTrue(Key.compareKeys(key1, key2, null) == 0);

	byte[] ba1 = { -1, -1, -1 };
	byte[] ba2 = { 0x7f, 0x7f, 0x7f };
	assertTrue(Key.compareKeys(ba1, ba2, null) > 0);

	try {
	    Key.compareKeys(key1, null, null);
	    fail("NullPointerException not caught");
	} catch (NullPointerException NPE) {
	}
    }
}
