/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *	Sleepycat Software.  All rights reserved.
 *
 * $Id: TestClassCatalog.java,v 1.14 2006/01/03 21:56:05 bostic Exp $
 */

package com.sleepycat.bind.serial.test;

import java.io.ObjectStreamClass;
import java.util.HashMap;

import com.sleepycat.bind.serial.ClassCatalog;
import com.sleepycat.je.DatabaseException;

/**
 * @author Mark Hayes
 */
public class TestClassCatalog implements ClassCatalog {

    private HashMap idToDescMap = new HashMap();
    private HashMap nameToIdMap = new HashMap();
    private int nextId = 1;

    public TestClassCatalog() {
    }

    public void close()
        throws DatabaseException {
    }

    public synchronized byte[] getClassID(ObjectStreamClass desc)
        throws DatabaseException {

        String className = desc.getName();
        byte[] id = (byte[]) nameToIdMap.get(className);
        if (id == null) {
            String strId = String.valueOf(nextId);
            id = strId.getBytes();
            nextId += 1;

            idToDescMap.put(strId, desc);
            nameToIdMap.put(className, id);
        }
        return id;
    }

    public synchronized ObjectStreamClass getClassFormat(byte[] id)
        throws DatabaseException {

        String strId = new String(id);
        ObjectStreamClass desc = (ObjectStreamClass) idToDescMap.get(strId);
        if (desc == null) {
            throw new DatabaseException("classID not found");
        }
        return desc;
    }
}
