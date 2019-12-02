/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Sleepycat Software.  All rights reserved.
 *
 * $Id: Renamer.java,v 1.2 2006/04/09 16:39:28 mark Exp $
 */

package com.sleepycat.persist.evolve;

/**
 * A mutation for renaming a class or field without changing the instance or
 * field value.  For example:
 * <pre class="code">
 *  package my.package;
 *
 *  // The old class.  Version 0 is implied.
 *  //
 *  {@literal @Entity}
 *  class Person {
 *      String name;
 *  }
 *
 *  // The new class.  A new version number must be assigned.
 *  //
 *  {@literal @Entity(version=1)}
 *  class Human {
 *      String fullName;
 *  }
 *
 *  // Add the mutations.
 *  //
 *  Mutations mutations = new Mutations();
 *
 *  mutations.addRenamer(new Renamer("my.package.Person", 0,
 *                                   Human.class.getName()));
 *
 *  mutations.addRenamer(new Renamer("my.package.Person", 0,
 *                                   "name", "fullName"));
 *
 *  // Configure the mutations as described {@link Mutations here}.</pre>
 *
 * @author Mark Hayes
 */
public class Renamer extends Mutation {

    /**
     * Creates a mutation for renaming the class of all instances of the given
     * class version.
     */
    public Renamer(String fromClass, int fromVersion, String toClass) {
        super(fromClass, fromVersion, null);
    }

    /**
     * Creates a mutation for renaming the given field for all instances of the
     * given class version.
     */
    public Renamer(String declaringClass, int declaringClassVersion,
                   String fromField, String toField) {
        super(declaringClass, declaringClassVersion, toField);
    }

    /**
     * Returns the new class or field name specified in the constructor.
     */
    public String getNewName() {
        return null;
    }
}
