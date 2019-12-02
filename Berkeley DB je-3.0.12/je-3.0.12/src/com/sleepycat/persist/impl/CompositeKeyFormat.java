/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Sleepycat Software.  All rights reserved.
 *
 * $Id: CompositeKeyFormat.java,v 1.13 2006/05/09 05:46:58 mark Exp $
 */

package com.sleepycat.persist.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import com.sleepycat.persist.model.ClassMetadata;
import com.sleepycat.persist.model.FieldMetadata;
import com.sleepycat.persist.raw.RawField;

/**
 * Format for a composite key class.
 *
 * This class is similar to ComplexFormat in that a composite key class and
 * other complex classes have fields, and the Accessor interface is used to
 * access those fields.  Composite key classes are different in the following
 * ways:
 *
 * - The superclass must be Object.  No inheritance is allowed.
 *
 * - All instance fields must be annotated with @KeyField, which determines
 *   their order in the data bytes.
 *
 * - Although fields may be reference types (primitive wrappers or other simple
 *   reference types), they are stored as if they were primitives.  No object
 *   format ID is stored, and the class of the object must be the declared
 *   classs of the field; i.e., no polymorphism is allowed for key fields.
 *   In other words, a composite key is stored as an ordinary tuple as defined
 *   in the com.sleepycat.bind.tuple package.  This keeps the key small and
 *   gives it a well defined sort order.
 *
 * - If the key class implements Comparable, it is called by the Database
 *   btree comparator.  It must therefore be available during JE recovery,
 *   before the store and catalog have been opened.  To support this, this
 *   format can be constructed during recovery.  A SimpleCatalog singleton
 *   instance is used to provide a catalog of simple types that is used by
 *   the composite key format.
 *
 * - When interacting with the Accessor, the composite key format treats the
 *   Accessor's non-key fields as its key fields.  The Accessor's key fields
 *   are secondary keys, while the composite format's key fields are the
 *   component parts of a single key.
 *
 * @author Mark Hayes
 */
public class CompositeKeyFormat extends Format {

    private static final long serialVersionUID = 306843428409314630L;

    private ClassMetadata metadata;
    private List<FieldInfo> fields;
    private transient Accessor objAccessor;
    private transient Accessor rawAccessor;
    private transient Map<String,RawField> rawFields;

    static String[] getFieldNameArray(List<FieldMetadata> list) {
        int index = 0;
        String[] a = new String[list.size()];
        for (FieldMetadata f : list) {
            a[index] = f.getName();
            index += 1;
        }
        return a;
    }

    CompositeKeyFormat(Class cls,
                       ClassMetadata metadata,
                       List<FieldMetadata> fieldNames) {
        this(cls, metadata, getFieldNameArray(fieldNames));
    }

    CompositeKeyFormat(Class cls,
                       ClassMetadata metadata,
                       String[] fieldNames) {
        super(cls);
        this.metadata = metadata;

        /* Check that the superclass is Object. */
        Class superCls = cls.getSuperclass();
        if (superCls != Object.class) {
            throw new IllegalArgumentException
                ("Composite key class must be derived from Object: " +
                 cls.getName());
        }

        /* Populate fields list in fieldNames order. */
        List<FieldInfo> instanceFields = FieldInfo.getInstanceFields(cls);
        fields = new ArrayList<FieldInfo>(instanceFields.size());
        for (String fieldName : fieldNames) {
            FieldInfo field = null;
            for (FieldInfo tryField : instanceFields) {
                if (fieldName.equals(tryField.getName())) {
                    field = tryField;
                    break;
                }
            }
            if (field == null) {
                throw new IllegalArgumentException
                    ("Composite key field is not an instance field:" +
                     getClassName() + '.' + fieldName);
            }
            fields.add(field);
            instanceFields.remove(field);
            if (!SimpleCatalog.isSimpleType(field.getFieldClass())) {
                throw new IllegalArgumentException
                    ("Composite key field is not a simple type: " +
                     getClassName() + '.' + fieldName);
            }
        }
        if (instanceFields.size() > 0) {
            throw new IllegalArgumentException
                ("All composite key instance fields must be key fields: " +
                 getClassName() + '.' + instanceFields.get(0).getName());
        }
    }

    @Override
    boolean isModelClass() {
        return true;
    }

    @Override
    ClassMetadata getClassMetadata() {
        if (metadata == null) {
            throw new IllegalStateException(getClassName());
        }
        return metadata;
    }

    @Override
    public Map<String,RawField> getFields() {

        /*
         * Lazily create the raw type information.  Synchronization is not
         * required since this object is immutable.  If by chance we create two
         * maps when two threads execute this block, no harm is done.  But be
         * sure to assign the rawFields field only after the map is fully
         * populated.
         */
        if (rawFields == null) {
            Map<String,RawField> map = new HashMap<String,RawField>();
            for (RawField field : fields) {
                map.put(field.getName(), field);
            }
            rawFields = map;
        }
        return rawFields;
    }

    @Override
    void collectRelatedFormats(Catalog catalog,
                               Map<String,Format> newFormats) {
        /* Collect field formats. */
        for (FieldInfo field : fields) {
            field.collectRelatedFormats(catalog, newFormats);
        }
    }

    @Override
    void initialize(Catalog catalog) {
        /* Initialize all fields. */
        for (FieldInfo field : fields) {
            field.initialize(catalog);
        }
        /* Create the accessor. */
        Class type = getType();
        if (EnhancedAccessor.isEnhanced(type)) {
            objAccessor = new EnhancedAccessor(type);
        } else {
            objAccessor = new ReflectionAccessor(catalog, type, fields);
        }
        rawAccessor = new RawAccessor(this, fields);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof CompositeKeyFormat) {
            CompositeKeyFormat o = (CompositeKeyFormat) other;
            return super.equals(o) &&
                   fields.equals(o.fields) &&
                   metadata.equals(o.metadata);
        } else {
            return false;
        }
    }

    @Override
    Object newInstance(EntityInput input, boolean rawAccess) {
        Accessor accessor = rawAccess ? rawAccessor : objAccessor;
        return accessor.newInstance();
    }
    
    @Override
    Object newArray(int len) {
        return objAccessor.newArray(len);
    }

    @Override
    void writeObject(Object o, EntityOutput output, boolean rawAccess) {
        Accessor accessor = rawAccess ? rawAccessor : objAccessor;
        accessor.writeNonKeyFields(o, output);
    }

    @Override
    void readObject(Object o, EntityInput input, boolean rawAccess) {
        Accessor accessor = rawAccess ? rawAccessor : objAccessor;
        accessor.readNonKeyFields(o, input, 0, Integer.MAX_VALUE, -1);
    }

    @Override
    void skipContents(EntityInput input) {
        int maxNum = fields.size();
        for (int i = 0; i < maxNum; i += 1) {
            fields.get(i).getType().skipContents(input);
        }
    }

    @Override
    void copySecKey(EntityInput input, EntityOutput output) {
        int maxNum = fields.size();
        for (int i = 0; i < maxNum; i += 1) {
            fields.get(i).getType().copySecKey(input, output);
        }
    }
}
