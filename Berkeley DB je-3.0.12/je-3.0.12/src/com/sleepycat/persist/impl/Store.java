/*-
 * See the file LICENSE for redistribution information.
 *
 * Copyright (c) 2002-2006
 *      Sleepycat Software.  All rights reserved.
 *
 * $Id: Store.java,v 1.7 2006/05/24 02:21:23 mark Exp $
 */

package com.sleepycat.persist.impl;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.ForeignKeyDeleteAction;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.Sequence;
import com.sleepycat.je.SequenceConfig;
import com.sleepycat.je.Transaction;
import com.sleepycat.persist.StoreConfig;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.persist.SecondaryIndex;
import com.sleepycat.persist.evolve.EvolveConfig;
import com.sleepycat.persist.evolve.EvolveStats;
import com.sleepycat.persist.evolve.Mutations;
import com.sleepycat.persist.model.ClassMetadata;
import com.sleepycat.persist.model.DeleteAction;
import com.sleepycat.persist.model.EntityMetadata;
import com.sleepycat.persist.model.EntityModel;
import com.sleepycat.persist.model.FieldMetadata;
import com.sleepycat.persist.model.ModelInternal;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.PrimaryKeyMetadata;
import com.sleepycat.persist.model.Relationship;
import com.sleepycat.persist.model.SecondaryKey;
import com.sleepycat.persist.model.SecondaryKeyMetadata;
import com.sleepycat.persist.raw.RawObject;
import static com.sleepycat.persist.model.DeleteAction.*;

/**
 * Base implementation for EntityStore,  RawStore and ConversionStore.  The
 * methods here correspond directly to those in EntityStore; see EntityStore
 * documentation for details.
 *
 * @author Mark Hayes
 */
public class Store {

    private static final char NAME_SEPARATOR = '#';
    private static final String NAME_PREFIX = "persist" + NAME_SEPARATOR;
    private static final String DB_NAME_PREFIX = "com.sleepycat.persist.";
    private static final String CATALOG_DB = DB_NAME_PREFIX + "formats";
    private static final String SEQUENCE_DB = DB_NAME_PREFIX + "sequences";

    private static Map<Environment,Map<String,PersistCatalog>> catalogPool =
        new HashMap<Environment,Map<String,PersistCatalog>>();

    private Environment env;
    private boolean rawAccess;
    private PersistCatalog catalog;
    private EntityModel model;
    private Mutations mutations;
    private StoreConfig storeConfig;
    private String storeName;
    private String storePrefix;
    private Map<String,PrimaryIndex> priIndexMap;
    private Map<String,SecondaryIndex> secIndexMap;
    private Map<String,DatabaseConfig> priConfigMap;
    private Map<String,SecondaryConfig> secConfigMap;
    private Map<String,PersistKeyBinding> keyBindingMap;
    private Map<String,Sequence> sequenceMap;
    private Map<String,SequenceConfig> sequenceConfigMap;
    private Database sequenceDb;

    public Store(Environment env,
                 String storeName,
                 StoreConfig config,
                 boolean rawAccess)
        throws DatabaseException {

        this.env = env;
        this.storeName = storeName;
        this.rawAccess = rawAccess;

        if (env == null || storeName == null) {
            throw new NullPointerException
                ("env and storeName parameters must not be null");
        }
        if (config != null) {
            model = config.getModel();
            mutations = config.getMutations();
        }
        if (config == null) {
            storeConfig = config = StoreConfig.DEFAULT;
        } else {
            storeConfig = config.cloneConfig();
        }

        storePrefix = NAME_PREFIX + storeName + NAME_SEPARATOR;
        priIndexMap = new HashMap<String,PrimaryIndex>();
        secIndexMap = new HashMap<String,SecondaryIndex>();
        priConfigMap = new HashMap<String,DatabaseConfig>();
        secConfigMap = new HashMap<String,SecondaryConfig>();
        keyBindingMap = new HashMap<String,PersistKeyBinding>();
        sequenceMap = new HashMap<String,Sequence>();
        sequenceConfigMap = new HashMap<String,SequenceConfig>();

        if (rawAccess) {
            /* Open a read-only catalog that does not use the current model. */
            if (model != null) {
                throw new IllegalArgumentException
                    ("A model may not be specified when opening a RawStore");
            }
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setReadOnly(true);
            dbConfig.setTransactional
                (storeConfig.getTransactional());
            catalog = new PersistCatalog
                (null, env, storePrefix + CATALOG_DB, dbConfig,
                 model, mutations, false /*useCurrentModel*/);
        } else {
            /* Open the shared catalog that uses the current model. */
            synchronized (catalogPool) {
                Map<String,PersistCatalog> catalogMap = catalogPool.get(env);
                if (catalogMap == null) {
                    catalogMap = new HashMap<String,PersistCatalog>();
                    catalogPool.put(env, catalogMap);
                }
                catalog = catalogMap.get(storeName);
                if (catalog != null) {
                    catalog.openExisting();
                } else {
                    Transaction txn = null;
                    if (storeConfig.getTransactional()) {
                        txn = env.beginTransaction(null, null);
                    }
                    boolean success = false;
                    try {
                        DatabaseConfig dbConfig = new DatabaseConfig();
                        dbConfig.setAllowCreate(storeConfig.getAllowCreate());
                        dbConfig.setReadOnly(storeConfig.getReadOnly());
                        dbConfig.setTransactional
                            (storeConfig.getTransactional());
                        catalog = new PersistCatalog
                            (txn, env, storePrefix + CATALOG_DB, dbConfig,
                             model, mutations, true /*useCurrentModel*/);
                        catalogMap.put(storeName, catalog);
                        success = true;
                    } finally {
                        if (txn != null) {
                            if (success) {
                                txn.commit();
                            } else {
                                txn.abort();
                            }
                        }
                    }
                }
            }
        }

        /* Get the merged mutations from the catalog. */
        mutations = catalog.getMutations();

        /*
         * If there is no model parameter, use the default or stored model
         * obtained from the catalog.
         */
        model = catalog.getResolvedModel();

        /* Give the model a reference to the catalog. */
        ModelInternal.setCatalog(model, catalog);
    }

    public Environment getEnvironment() {
        return env;
    }

    public StoreConfig getConfig() {
        return storeConfig.cloneConfig();
    }

    public String getStoreName() {
        return storeName;
    }

    public static Set<String> getStoreNames(Environment env)
        throws DatabaseException {

        Set<String> set = new HashSet<String>();
        for (Object o : env.getDatabaseNames()) {
            String s = (String) o;
            if (s.startsWith(NAME_PREFIX)) {
                int start = NAME_PREFIX.length();
                int end = s.indexOf(start, NAME_SEPARATOR);
                set.add(s.substring(start, end));
            }
        }
        return set;
    }

    public EntityModel getModel() {
        return model;
    }

    public Mutations getMutations() {
        return mutations;
    }

    /**
     * A getPrimaryIndex with extra parameters for opening a raw store.
     * primaryKeyClass and entityClass are used for generic typing; for a raw
     * store, these should always be Object.class and RawObject.class.
     * primaryKeyClassName is used for consistency checking and should be null
     * for a raw store only.  entityClassName is used to identify the store and
     * may not be null.
     */
    public synchronized <PK,E> PrimaryIndex<PK,E>
        getPrimaryIndex(Class<PK> primaryKeyClass,
                        String primaryKeyClassName,
                        Class<E> entityClass,
                        String entityClassName)
        throws DatabaseException {

        assert (rawAccess && entityClass == RawObject.class) ||
              (!rawAccess && entityClass != RawObject.class);
        assert (rawAccess && primaryKeyClassName == null) ||
              (!rawAccess && primaryKeyClassName != null);

        checkOpen();

        PrimaryIndex<PK,E> priIndex = priIndexMap.get(entityClassName);
        if (priIndex == null) {

            /* Check metadata. */
            EntityMetadata entityMeta = checkEntityClass(entityClassName);
            PrimaryKeyMetadata priKeyMeta = entityMeta.getPrimaryKey();
            if (primaryKeyClassName == null) {
                primaryKeyClassName = priKeyMeta.getClassName();
            } else {
                String expectClsName =
                    SimpleCatalog.keyClassName(priKeyMeta.getClassName());
                if (!primaryKeyClassName.equals(expectClsName)) {
                    throw new IllegalArgumentException
                        ("Wrong primary key class: " + primaryKeyClassName +
                         " Correct class is: " + expectClsName);
                }
            }

            /* Create bindings. */
            PersistEntityBinding entityBinding =
                new PersistEntityBinding(catalog, entityClassName, rawAccess);
            PersistKeyBinding keyBinding = getKeyBinding(primaryKeyClassName);

            /* Get the primary key sequence. */
            String seqName = priKeyMeta.getSequenceName();
            if (seqName != null) {
                entityBinding.keyAssigner = new PersistKeyAssigner
                    (keyBinding, entityBinding, getSequence(seqName));
            }

            /* Use a single transaction for all opens. */
            Transaction txn = null;
            DatabaseConfig dbConfig = getPrimaryConfig(entityMeta);
            if (dbConfig.getTransactional()) {
                txn = env.beginTransaction(null, null);
            }
            boolean success = false;
            try {
                /* Open the primary database. */
                String dbName = storePrefix + entityClassName;
                Database db = env.openDatabase(txn, dbName, dbConfig);

                /* Create index object. */
                priIndex = new PrimaryIndex
                    (db, primaryKeyClass, keyBinding, entityClass,
                     entityBinding);
                priIndexMap.put(entityClassName, priIndex);

                /* If not read-only, open all associated secondaries. */
                if (!dbConfig.getReadOnly()) {
                    for (SecondaryKeyMetadata secKeyMeta :
                         entityMeta.getSecondaryKeys().values()) {
                        String keyClassName = getSecKeyClass(secKeyMeta);
                        Class keyClass =
                            SimpleCatalog.keyClassForName(keyClassName);
                        openSecondaryIndex
                            (txn, priIndex, entityMeta, keyClass, keyClassName, 
                             secKeyMeta, makeSecName
                                (entityClassName, secKeyMeta.getKeyName()));
                    }
                }
                success = true;
            } finally {
                if (txn != null) {
                    if (success) {
                        txn.commit();
                    } else {
                        txn.abort();
                    }
                }
            }
        }
        return priIndex;
    }

    /**
     * A getSecondaryIndex with extra parameters for opening a raw store.
     * keyClassName is used for consistency checking and should be null for a
     * raw store only.
     */
    public synchronized <SK,PK,E> SecondaryIndex<SK,PK,E>
        getSecondaryIndex(PrimaryIndex<PK,E> primaryIndex,
                          String entityClassName,
                          Class<SK> keyClass,
                          String keyClassName,
                          String keyName)
        throws DatabaseException {

        assert (rawAccess && keyClassName == null) ||
              (!rawAccess && keyClassName != null);

        checkOpen();

        /*
         * Even though the primary is already open, we can't assume the
         * secondary is open because we don't automatically open all
         * secondaries when the primary is read-only.  Use auto-commit (a null
         * transaction) since we're opening only one database.
         */
        String secName = makeSecName(entityClassName, keyName);
        SecondaryIndex<SK,PK,E> secIndex = secIndexMap.get(secName);
        if (secIndex == null) {
            EntityMetadata entityMeta =
                model.getEntityMetadata(entityClassName);
            assert entityMeta != null;
            SecondaryKeyMetadata secKeyMeta = checkSecKey(entityMeta, keyName);
            secIndex = openSecondaryIndex
                (null, primaryIndex, entityMeta, keyClass, keyClassName,
                 secKeyMeta, secName);
        }
        return secIndex;
    }

    /**
     * Opens a secondary index with a given transaction and adds it to the
     * secIndexMap.  We assume that the index is not already open.
     */
    private <SK,PK,E> SecondaryIndex<SK,PK,E>
        openSecondaryIndex(Transaction txn,
                           PrimaryIndex<PK,E> primaryIndex,
                           EntityMetadata entityMeta,
                           Class<SK> keyClass,
                           String keyClassName,
                           SecondaryKeyMetadata secKeyMeta,
                           String secName)
        throws DatabaseException {

        assert !secIndexMap.containsKey(secName);
        String keyName = secKeyMeta.getKeyName();
        String dbName = storePrefix + secName;
        SecondaryConfig config =
            getSecondaryConfig(secName, entityMeta, keyClassName, secKeyMeta);
        Database priDb = primaryIndex.getDatabase();
        DatabaseConfig priConfig = priDb.getConfig();

        String relatedClsName = secKeyMeta.getRelatedEntity();
        if (relatedClsName != null) {
            PrimaryIndex relatedIndex = priIndexMap.get(relatedClsName);
            if (relatedIndex == null) {
                EntityMetadata relatedEntityMeta =
                    checkEntityClass(relatedClsName);
                Class relatedKeyCls;
                String relatedKeyClsName;
                Class relatedCls;
                if (rawAccess) {
                    relatedCls = RawObject.class;
                    relatedKeyCls = Object.class;
                    relatedKeyClsName = null;
                } else {
                    try {
                        relatedCls = Class.forName(relatedClsName);
                    } catch (ClassNotFoundException e) {
                        throw new IllegalArgumentException
                            ("Foreign key database class not found: " +
                             relatedClsName);
                    }
                    relatedKeyClsName = SimpleCatalog.keyClassName
                        (relatedEntityMeta.getPrimaryKey().getClassName());
                    relatedKeyCls =
                        SimpleCatalog.keyClassForName(relatedKeyClsName);
                }
                /* XXX Check to make sure cycles are not possible here. */
                relatedIndex = getPrimaryIndex
                    (relatedKeyCls, relatedKeyClsName,
                     relatedCls, relatedClsName);
            }
            config.setForeignKeyDatabase(relatedIndex.getDatabase());
        }

        if (config.getTransactional() != priConfig.getTransactional() ||
            config.getReadOnly() != priConfig.getReadOnly()) {
            throw new IllegalArgumentException
                ("One of these properties was changed to be inconsistent" +
                 " with the associated primary database: " +
                 " Transactional, ReadOnly");
        }

        PersistKeyBinding keyBinding = getKeyBinding(keyClassName);
        
        SecondaryDatabase db =
            env.openSecondaryDatabase(txn, dbName, priDb, config);
        SecondaryIndex<SK,PK,E> secIndex = new SecondaryIndex
            (db, null, primaryIndex, keyClass, keyBinding);
        secIndexMap.put(secName, secIndex);
        return secIndex;
    }

    public EvolveStats evolve(EvolveConfig config)
        throws DatabaseException {

        checkOpen();
        throw new UnsupportedOperationException();
    }

    public void truncateClass(Class entityClass)
        throws DatabaseException {
        
        truncateClass(null, entityClass);
    }

    public synchronized void truncateClass(Transaction txn, Class entityClass)
        throws DatabaseException {

        checkOpen();

        /* Close primary and secondary databases. */
        closeClass(entityClass);

        String clsName = entityClass.getName();
        EntityMetadata entityMeta = checkEntityClass(clsName);

        /*
         * Truncate the primary first and let any exceptions propogate
         * upwards.  Then truncate each primary, only throwing the first
         * exception.
         */
        String dbName = storePrefix + clsName;
        boolean primaryExists = true;
        try {
            env.truncateDatabase(txn, dbName, false);
        } catch (DatabaseNotFoundException ignored) {
            primaryExists = false;
        }
        if (primaryExists) {
            DatabaseException firstException = null;
            for (SecondaryKeyMetadata keyMeta :
                 entityMeta.getSecondaryKeys().values()) {
                try {
                    env.truncateDatabase
                        (txn,
                         storePrefix +
                         makeSecName(clsName, keyMeta.getKeyName()),
                         false);
                } catch (DatabaseNotFoundException ignored) {
                    /* Ignore secondaries that do not exist. */
                } catch (DatabaseException e) {
                    if (firstException == null) {
                        firstException = e;
                    }
                }
            }
            if (firstException != null) {
                throw firstException;
            }
        }
    }

    public synchronized void closeClass(Class entityClass)
        throws DatabaseException {

        checkOpen();
        String clsName = entityClass.getName();
        EntityMetadata entityMeta = checkEntityClass(clsName);

        PrimaryIndex priIndex = priIndexMap.get(clsName);
        if (priIndex != null) {
            /* Close the secondaries first. */
            DatabaseException firstException = null;
            for (SecondaryKeyMetadata keyMeta :
                 entityMeta.getSecondaryKeys().values()) {

                String secName = makeSecName(clsName, keyMeta.getKeyName());
                SecondaryIndex secIndex = secIndexMap.get(secName);
                if (secIndex != null) {
                    firstException =
                        closeDb(secIndex.getDatabase(), firstException);
                    firstException =
                        closeDb(secIndex.getKeysDatabase(), firstException);
                    secIndexMap.remove(secName);
                }
            }
            /* Close the primary last. */
            firstException =
                closeDb(priIndex.getDatabase(), firstException);
            priIndexMap.remove(clsName);

            /* Throw the first exception encountered. */
            if (firstException != null) {
                throw firstException;
            }
        }
    }

    public synchronized void close()
        throws DatabaseException {

        checkOpen();
        DatabaseException firstException = null;
        try {
            synchronized (catalogPool) {
                Map<String,PersistCatalog> catalogMap = catalogPool.get(env);
                assert catalogMap != null;
                if (catalog.close()) {
                    /* Remove only when the reference count goes to zero. */
                    catalogMap.remove(storeName);
                }
                catalog = null;
            }
        } catch (DatabaseException e) {
            if (firstException == null) {
                firstException = e;
            }
        }
        firstException = closeDb(sequenceDb, firstException);
        for (SecondaryIndex index : secIndexMap.values()) {
            firstException = closeDb(index.getDatabase(), firstException);
            firstException = closeDb(index.getKeysDatabase(), firstException);
        }
        for (PrimaryIndex index : priIndexMap.values()) {
            firstException = closeDb(index.getDatabase(), firstException);
        }
        if (firstException != null) {
            throw firstException;
        }
    }

    public synchronized Sequence getSequence(String name)
        throws DatabaseException {

        checkOpen();

        if (storeConfig.getReadOnly()) {
            throw new IllegalStateException("Store is read-only");
        }
        
        Sequence seq = sequenceMap.get(name);
        if (seq == null) {
            if (sequenceDb == null) {
                String dbName = storePrefix + SEQUENCE_DB;
                DatabaseConfig dbConfig = new DatabaseConfig();
                dbConfig.setTransactional(storeConfig.getTransactional());
                dbConfig.setAllowCreate(true);
                sequenceDb = env.openDatabase(null, dbName, dbConfig);
            }
            DatabaseEntry entry = new DatabaseEntry();
            StringBinding.stringToEntry(name, entry);
            seq = sequenceDb.openSequence(null, entry, getSequenceConfig(name));
            sequenceMap.put(name, seq);
        }
        return seq;
    }

    public synchronized SequenceConfig getSequenceConfig(String name) {
        checkOpen();
        SequenceConfig config = sequenceConfigMap.get(name);
        if (config == null) {
            config = new SequenceConfig();
            config.setInitialValue(1);
            config.setRange(1, Long.MAX_VALUE);
            config.setCacheSize(100);
            config.setAutoCommitNoSync(true);
            config.setAllowCreate(!storeConfig.getReadOnly());
            sequenceConfigMap.put(name, config);
        }
        return config;
    }

    public synchronized void setSequenceConfig(String name,
                                               SequenceConfig config) {
        checkOpen();
        sequenceConfigMap.put(name, config);
    }

    public synchronized DatabaseConfig getPrimaryConfig(Class entityClass) {
        checkOpen();
        String clsName = entityClass.getName();
        EntityMetadata meta = checkEntityClass(clsName);
        return getPrimaryConfig(meta).cloneConfig();
    }

    private synchronized DatabaseConfig getPrimaryConfig(EntityMetadata meta) {
        String clsName = meta.getClassName();
        DatabaseConfig config = priConfigMap.get(clsName);
        if (config == null) {
            config = new DatabaseConfig();
            config.setTransactional(storeConfig.getTransactional());
            config.setAllowCreate(!storeConfig.getReadOnly());
            config.setReadOnly(storeConfig.getReadOnly());
            setBtreeComparator(config, meta.getPrimaryKey().getClassName());
            priConfigMap.put(clsName, config);
        }
        return config;
    }

    public synchronized void setPrimaryConfig(Class entityClass,
                                              DatabaseConfig config) {
        checkOpen();
        String clsName = entityClass.getName();
        if (priIndexMap.containsKey(clsName)) {
            throw new IllegalStateException
                ("Cannot set config after DB is open");
        }
        EntityMetadata meta = checkEntityClass(clsName);
        DatabaseConfig dbConfig = getPrimaryConfig(meta);
        if (config.getSortedDuplicates() ||
            config.getBtreeComparator() != dbConfig.getBtreeComparator()) {
            throw new IllegalArgumentException
                ("One of these properties was illegally changed: " +
                 " SortedDuplicates or BtreeComparator");
        }
        priConfigMap.put(clsName, config);
    }

    public synchronized SecondaryConfig getSecondaryConfig(Class entityClass,
                                                           String keyName) {
        checkOpen();
        String entityClsName = entityClass.getName();
        EntityMetadata entityMeta = checkEntityClass(entityClsName);
        SecondaryKeyMetadata secKeyMeta = checkSecKey(entityMeta, keyName);
        String keyClassName = getSecKeyClass(secKeyMeta);
        String secName = makeSecName(entityClass.getName(), keyName);
        return (SecondaryConfig) getSecondaryConfig
            (secName, entityMeta, keyClassName, secKeyMeta).cloneConfig();
    }

    private SecondaryConfig getSecondaryConfig(String secName,
                                               EntityMetadata entityMeta,
                                               String keyClassName,
                                               SecondaryKeyMetadata
                                               secKeyMeta) {
        SecondaryConfig config = secConfigMap.get(secName);
        if (config == null) {
            /* Set common properties to match the primary DB. */
            DatabaseConfig priConfig = getPrimaryConfig(entityMeta);
            config = new SecondaryConfig();
            config.setTransactional(priConfig.getTransactional());
            config.setAllowCreate(!priConfig.getReadOnly());
            config.setReadOnly(priConfig.getReadOnly());
            /* Set secondary properties based on metadata. */
            Relationship rel = secKeyMeta.getRelationship();
            config.setSortedDuplicates(rel == Relationship.MANY_TO_ONE ||
                                       rel == Relationship.MANY_TO_MANY);
            setBtreeComparator(config, secKeyMeta.getClassName());
            PersistKeyCreator keyCreator = new PersistKeyCreator
                (catalog, entityMeta, keyClassName, secKeyMeta);
            if (rel == Relationship.ONE_TO_MANY ||
                rel == Relationship.MANY_TO_MANY) {
                config.setMultiKeyCreator(keyCreator);
            } else {
                config.setKeyCreator(keyCreator);
            }
            DeleteAction deleteAction = secKeyMeta.getDeleteAction();
            if (deleteAction != null) {
                ForeignKeyDeleteAction baseDeleteAction;
                switch (deleteAction) {
                case ABORT:
                    baseDeleteAction = ForeignKeyDeleteAction.ABORT;
                    break;
                case CASCADE:
                    baseDeleteAction = ForeignKeyDeleteAction.CASCADE;
                    break;
                case NULLIFY:
                    baseDeleteAction = ForeignKeyDeleteAction.NULLIFY;
                    break;
                default:
                    throw new IllegalStateException(deleteAction.toString());
                }
                config.setForeignKeyDeleteAction(baseDeleteAction);
                if (deleteAction == DeleteAction.NULLIFY) {
                    config.setForeignMultiKeyNullifier(keyCreator);
                }
            }

            /*
             * XXX For class evolution: Set allow populate if the secondary key
             * was added.
             */
            secConfigMap.put(secName, config);
        }
        return config;
    }

    public synchronized void setSecondaryConfig(Class entityClass,
                                                String keyName,
                                                SecondaryConfig config) {
        checkOpen();
        String entityClsName = entityClass.getName();
        EntityMetadata entityMeta = checkEntityClass(entityClsName);
        SecondaryKeyMetadata secKeyMeta = checkSecKey(entityMeta, keyName);
        String keyClassName = getSecKeyClass(secKeyMeta);
        String secName = makeSecName(entityClass.getName(), keyName);
        if (secIndexMap.containsKey(secName)) {
            throw new IllegalStateException
                ("Cannot set config after DB is open");
        }
        SecondaryConfig dbConfig =
            getSecondaryConfig(secName, entityMeta, keyClassName, secKeyMeta);
        if (config.getSortedDuplicates() != dbConfig.getSortedDuplicates() ||
            config.getBtreeComparator() != dbConfig.getBtreeComparator() ||
            config.getDuplicateComparator() != null ||
            config.getAllowPopulate() != dbConfig.getAllowPopulate() ||
            config.getKeyCreator() != dbConfig.getKeyCreator() ||
            config.getMultiKeyCreator() != dbConfig.getMultiKeyCreator() ||
            config.getForeignKeyNullifier() !=
                dbConfig.getForeignKeyNullifier() ||
            config.getForeignMultiKeyNullifier() !=
                dbConfig.getForeignMultiKeyNullifier() ||
            config.getForeignKeyDeleteAction() !=
                dbConfig.getForeignKeyDeleteAction() ||
            config.getForeignKeyDatabase() != null) {
            throw new IllegalArgumentException
                ("One of these properties was illegally changed: " +
                 " SortedDuplicates, BtreeComparator, DuplicateComparator," +
                 " AllowPopulate, KeyCreator, MultiKeyCreator," +
                 " ForeignKeyNullifer, ForeignMultiKeyNullifier," +
                 " ForeignKeyDeleteAction, ForeignKeyDatabase");
        }
        secConfigMap.put(secName, config);
    }

    private String makeSecName(String entityClsName, String keyName) {
         return entityClsName + NAME_SEPARATOR + keyName;
    }

    private void checkOpen() {
        if (catalog == null) {
            throw new IllegalStateException("Store has been closed");
        }
    }

    private EntityMetadata checkEntityClass(String clsName) {
        EntityMetadata meta = model.getEntityMetadata(clsName);
        if (meta == null) {
            throw new IllegalArgumentException
                ("Not an entity class: " + clsName);
        }
        return meta;
    }

    private SecondaryKeyMetadata checkSecKey(EntityMetadata entityMeta,
                                             String keyName) {
        SecondaryKeyMetadata secKeyMeta =
            entityMeta.getSecondaryKeys().get(keyName);
        if (secKeyMeta == null) {
            throw new IllegalArgumentException
                ("Not a secondary key: " +
                 makeSecName(entityMeta.getClassName(), keyName));
        }
        return secKeyMeta;
    }

    private String getSecKeyClass(SecondaryKeyMetadata secKeyMeta) {
        String clsName = secKeyMeta.getElementClassName();
        if (clsName == null) {
            clsName = secKeyMeta.getClassName();
        }
        return SimpleCatalog.keyClassName(clsName);
    }

    private PersistKeyBinding getKeyBinding(String keyClassName) {
        PersistKeyBinding binding = keyBindingMap.get(keyClassName);
        if (binding == null) {
            binding = new PersistKeyBinding(catalog, keyClassName, rawAccess);
            keyBindingMap.put(keyClassName, binding);
        }
        return binding;
    }

    private void setBtreeComparator(DatabaseConfig config, String clsName) {
        if (!rawAccess) {
            ClassMetadata meta = model.getClassMetadata(clsName);
            if (meta != null) {
                List<FieldMetadata> compositeKeyFields =
                    meta.getCompositeKeyFields();
                if (compositeKeyFields != null) {
                    Class keyClass = SimpleCatalog.keyClassForName(clsName);
                    if (Comparable.class.isAssignableFrom(keyClass)) {
                        Comparator<Object> cmp = new PersistComparator
                            (clsName, compositeKeyFields,
                             getKeyBinding(clsName));
                        config.setBtreeComparator(cmp);
                    }
                }
            }
        }
    }

    private DatabaseException closeDb(Database db,
                                      DatabaseException firstException) {
        if (db != null) {
            try {
                db.close();
            } catch (DatabaseException e) {
                if (firstException == null) {
                    firstException = e;
                }
            }
        }
        return firstException;
    }
}
