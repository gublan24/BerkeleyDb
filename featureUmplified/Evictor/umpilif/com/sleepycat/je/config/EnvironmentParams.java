/*PLEASE DO NOT EDIT THIS CODE*/
/*This code was generated using the UMPLE 1.29.1.4260.b21abf3a3 modeling language!*/

package com.sleepycat.je.config;

// line 3 "../../../../EnvironmentParams.ump"
public class EnvironmentParams
{

  //------------------------
  // MEMBER VARIABLES
  //------------------------

  //------------------------
  // CONSTRUCTOR
  //------------------------

  public EnvironmentParams()
  {}

  //------------------------
  // INTERFACE
  //------------------------

  public void delete()
  {}
  
  //------------------------
  // DEVELOPER CODE - PROVIDED AS-IS
  //------------------------
  
  // line 5 ../../../../EnvironmentParams.ump
  public static final LongConfigParam EVICTOR_EVICT_BYTES = new LongConfigParam("je.evictor.evictBytes",
	    new Long(1024), null, new Long(524288), false,
	    "# When eviction happens, the evictor will push memory usage to this\n"
		    + "# number of bytes below je.maxMemory.  The default is 512KB and the\n"
		    + "# minimum is 1 KB (1024).");

    public static final IntConfigParam EVICTOR_USEMEM_FLOOR = new IntConfigParam("je.evictor.useMemoryFloor",
	    new Integer(50), new Integer(100), new Integer(95), false,
	    "# When eviction happens, the evictor will push memory usage to this\n" + "# percentage of je.maxMemory."
		    + "# (deprecated in favor of je.evictor.evictBytes");

    public static final IntConfigParam EVICTOR_NODE_SCAN_PERCENTAGE = new IntConfigParam(
	    "je.evictor.nodeScanPercentage", new Integer(1), new Integer(100), new Integer(10), false,
	    "# The evictor percentage of total nodes to scan per wakeup.\n"
		    + "# (deprecated in favor of je.evictor.nodesPerScan");

    public static final IntConfigParam EVICTOR_EVICTION_BATCH_PERCENTAGE = new IntConfigParam(
	    "je.evictor.evictionBatchPercentage", new Integer(1), new Integer(100), new Integer(10), false,
	    "# The evictor percentage of scanned nodes to evict per wakeup.\n" + "# (deprecated)");

    public static final IntConfigParam EVICTOR_NODES_PER_SCAN = new IntConfigParam("je.evictor.nodesPerScan",
	    new Integer(1), new Integer(1000), new Integer(10), false, "# The number of nodes in one evictor scan");

    public static final IntConfigParam EVICTOR_CRITICAL_PERCENTAGE = new IntConfigParam("je.evictor.criticalPercentage",
	    new Integer(0), new Integer(1000), new Integer(0), false,
	    "# At this percentage over the allotted cache, critical eviction\n" + "# will start."
		    + "# (deprecated, eviction is performed in-line");

    public static final BooleanConfigParam EVICTOR_LRU_ONLY = new BooleanConfigParam("je.evictor.lruOnly", true, false,
	    "# If true (the default), use an LRU-only policy to select nodes for\n"
		    + "# eviction.  If false, select by Btree level first, and then by LRU.");
  
}