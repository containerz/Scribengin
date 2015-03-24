package com.neverwinterdp.scribengin.dataflow.test;

import com.beust.jcommander.Parameter;
import com.neverwinterdp.scribengin.ScribenginClient;
import com.neverwinterdp.scribengin.storage.StorageDescriptor;

abstract public class DataflowSinkValidator implements Runnable {
  @Parameter(names = "--sink-name", required=true, description = "The storage sink name, usually the database name or dir name of the storage")
  protected String sinkName ;
  
  @Parameter(names = "--sink-expect-records", description = "The expect number of records in the sink")
  protected long    expectRecords = 100000;
  
  public void setExpectRecords(long num) { this.expectRecords = num ; }
  
  abstract public StorageDescriptor getSinkDescriptor() ;
  
  abstract public void init(ScribenginClient scribenginClient) ;
  
  abstract public void run() ;
  
  abstract public void runInBackground() ;
  
  abstract public boolean waitForTermination() throws InterruptedException;
  
  abstract public boolean waitForTermination(long timeout) throws InterruptedException;
  
  abstract public void populate(DataflowTestReport report) ;
}