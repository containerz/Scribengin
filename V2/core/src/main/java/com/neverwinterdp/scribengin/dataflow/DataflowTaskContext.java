package com.neverwinterdp.scribengin.dataflow;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.neverwinterdp.scribengin.Record;
import com.neverwinterdp.scribengin.sink.Sink;
import com.neverwinterdp.scribengin.sink.SinkFactory;
import com.neverwinterdp.scribengin.sink.SinkStream;
import com.neverwinterdp.scribengin.sink.SinkStreamDescriptor;
import com.neverwinterdp.scribengin.sink.SinkStreamWriter;
import com.neverwinterdp.scribengin.source.CommitPoint;
import com.neverwinterdp.scribengin.source.Source;
import com.neverwinterdp.scribengin.source.SourceFactory;
import com.neverwinterdp.scribengin.source.SourceStream;
import com.neverwinterdp.scribengin.source.SourceStreamDescriptor;
import com.neverwinterdp.scribengin.source.SourceStreamReader;

public class DataflowTaskContext {
  private DataflowTaskReport report ;
  private SourceContext sourceContext ;
  private Map<String, SinkContext> sinkContexts = new HashMap<String, SinkContext>();
  private DataflowRegistry dataflowRegistry;
  private DataflowTaskDescriptor dataflowTaskDescriptor;
  
  public DataflowTaskContext(DataflowContainer container, DataflowTaskDescriptor descriptor, DataflowTaskReport report) throws Exception {
    this.sourceContext = new SourceContext(container.getSourceFactory(), descriptor.getSourceStreamDescriptor());
    Iterator<Map.Entry<String, SinkStreamDescriptor>> i = descriptor.getSinkStreamDescriptors().entrySet().iterator() ;
    while(i.hasNext()) {
      Map.Entry<String, SinkStreamDescriptor> entry = i.next();
      SinkContext context = new SinkContext(container.getSinkFactory(), entry.getValue());
      sinkContexts.put(entry.getKey(), context) ;
    }
    this.report = report ;
    this.dataflowTaskDescriptor = descriptor;
    this.dataflowRegistry = container.getDataflowRegistry();
  }
  
  public DataflowTaskReport getReport() { return this.report ;}
  
  public SourceStreamReader getSourceStreamReader() {
    return sourceContext.assignedSourceStreamReader;
  }
  
  public void append(Record record) throws Exception {
    SinkContext sinkContext = sinkContexts.get("default") ;
    sinkContext.assignedSinkStreamWriter.append(record);
  }
  
  public void write(String sinkName, Record record) throws Exception {
    SinkContext sinkContext = sinkContexts.get(sinkName) ;
    sinkContext.assignedSinkStreamWriter.append(record);
  }
  
  
  private boolean prepareCommit() throws Exception {
    boolean retVal = sourceContext.assignedSourceStreamReader.prepareCommit();
    
    Iterator<SinkContext> i = sinkContexts.values().iterator();
    while(i.hasNext()) {
      SinkContext ctx = i.next();
      if(!ctx.prepareCommit()){
        retVal = false;
      }
    }
    
    return retVal;
  }
  
  public boolean commit() throws Exception {
    

    //prepareCommit is a vote to make sure both sink, invalidSink, and source
    //are ready to commit data, otherwise rollback will occur
    if(prepareCommit()){
      //The actual committing of data
      //TODO: What to do with this - 
      //What do we do with this commitpoint here?
      //Check into registry or something?
      //Something missing here to help coordinate with ZK?
      //Or should that be the job of the source/sink stream writers?
      CommitPoint cp = sourceContext.commit();
      
      boolean commitStatus = true;
      Iterator<SinkContext> i = sinkContexts.values().iterator();
      while(i.hasNext()) {
        SinkContext ctx = i.next();
        if(!ctx.commit()){
          commitStatus = false;
        }
      }
      
      
      if(commitStatus){
        //update any offsets that need to be managed, clear temp data
        completeCommit();
        report.incrCommitProcessCount();
        return true;
      }
      else{
        //Undo anything that could have gone wrong,
        //undo any commits, go back as if nothing happened
        rollback();
        throw new CommitException("Failed to commit!");
      }
    }
    sourceContext.commit();
    report.incrCommitProcessCount();
    dataflowRegistry.dataflowTaskReport(dataflowTaskDescriptor, report);
    return false;
  }
  
  private void rollback() throws Exception {
    //TODO: implement the proper transaction
    Iterator<SinkContext> i = sinkContexts.values().iterator();
    while(i.hasNext()) {
      SinkContext ctx = i.next();
      ctx.rollback();
    }
    sourceContext.rollback();
  }
  
  public void close() throws Exception {
    //TODO: implement the proper transaction
    Iterator<SinkContext> i = sinkContexts.values().iterator();
    while(i.hasNext()) {
      SinkContext ctx = i.next();
      ctx.close();;
    }
    sourceContext.close();
  }
  
  private void completeCommit() {
    // TODO Auto-generated method stub
    sourceContext.assignedSourceStreamReader.completeCommit();
    
    Iterator<SinkContext> i = sinkContexts.values().iterator();
    while(i.hasNext()) {
      SinkContext ctx = i.next();
      ctx.completeCommit();
    }
  }
  
  public void clearBuffer() {
    sourceContext.assignedSourceStreamReader.clearBuffer();
    
    Iterator<SinkContext> i = sinkContexts.values().iterator();
    while(i.hasNext()) {
      SinkContext ctx = i.next();
      ctx.clearBuffer();
    }
  }
  
  static public class  SourceContext {
    private Source source ;
    private SourceStream assignedSourceStream ;
    private SourceStreamReader assignedSourceStreamReader;
  
    public SourceContext(SourceFactory factory, SourceStreamDescriptor streamDescriptor) throws Exception {
      this.source = factory.create(streamDescriptor) ;
      this.assignedSourceStream = source.getStream(streamDescriptor.getId());
      this.assignedSourceStreamReader = assignedSourceStream.getReader("DataflowTask");
    }
    
    public CommitPoint commit() throws Exception {
      return assignedSourceStreamReader.commit();
    }
    
    public void rollback() throws Exception {
      assignedSourceStreamReader.rollback();
    }
    
    public void close() throws Exception {
      assignedSourceStreamReader.close();
    }
  }
  
  static public class  SinkContext {
    private Sink sink ;
    private SinkStream assignedSinkStream ;
    private SinkStreamWriter assignedSinkStreamWriter;
  
    
    public SinkContext(SinkFactory factory, SinkStreamDescriptor streamDescriptor) throws Exception {
      this.sink = factory.create(streamDescriptor);
      this.assignedSinkStream = sink.getStream(streamDescriptor);
      this.assignedSinkStreamWriter = this.assignedSinkStream.getWriter();
    }
    
    public void clearBuffer() {
      assignedSinkStreamWriter.clearBuffer();
    }

    public void completeCommit() {
      // TODO Auto-generated method stub
      
    }

    public boolean prepareCommit() throws Exception{
      return assignedSinkStreamWriter.prepareCommit();
    }

    public boolean commit() throws Exception {
      return assignedSinkStreamWriter.commit();
    }
    
    public void rollback() throws Exception {
      assignedSinkStreamWriter.rollback();
    }
    
    public void close() throws Exception {
      assignedSinkStreamWriter.close();
    }
  }

  class CommitException extends Exception {
    public CommitException(String message) {
      super(message);
    }
  }
}