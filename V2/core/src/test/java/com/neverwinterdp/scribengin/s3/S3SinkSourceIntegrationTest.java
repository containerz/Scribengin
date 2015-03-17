package com.neverwinterdp.scribengin.s3;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.neverwinterdp.scribengin.Record;
import com.neverwinterdp.scribengin.s3.sink.S3Sink;
import com.neverwinterdp.scribengin.sink.SinkDescriptor;
import com.neverwinterdp.scribengin.sink.SinkStream;
import com.neverwinterdp.scribengin.sink.SinkStreamWriter;

public class S3SinkSourceIntegrationTest {
  static public String BUCKET_NAME = "sink-source-test";
  static public String STORAGE_PATH = "database";
  
  static S3Client s3Client ;
  
  @BeforeClass
  static public void beforeClass() {
    s3Client = new S3Client() ;
    s3Client.onInit();
    if(s3Client.hasBucket(BUCKET_NAME)) {
      s3Client.deleteBucket(BUCKET_NAME, true);
    }
    s3Client.createBucket(BUCKET_NAME);
    s3Client.createS3Folder(BUCKET_NAME, STORAGE_PATH);
    s3Client.createS3Folder(BUCKET_NAME, STORAGE_PATH + "/stream-0");
    s3Client.createS3Folder(BUCKET_NAME, STORAGE_PATH + "/stream-1");
  }
  
  @AfterClass
  static public void afterClass() {
    s3Client.onDestroy();
  }
  
  @Test
  public void testSink() throws Exception {
    SinkDescriptor sinkDescriptor = new SinkDescriptor() ;
    sinkDescriptor.attribute("s3.bucket.name",  BUCKET_NAME);
    sinkDescriptor.attribute("s3.storage.path", STORAGE_PATH);
    S3Sink sink = new S3Sink(s3Client, sinkDescriptor);
    Assert.assertNotNull(sink.getSinkFolder());
    SinkStream stream3 = sink.newStream() ;
    SinkStream[] streams = sink.getStreams() ;
    Assert.assertEquals(3, streams.length);
    
    for(int i = 0; i < 3; i++) {
      SinkStreamWriter writer = stream3.getWriter();
      for(int j = 0; j < 100; j ++) {
        String key = "stream=" + stream3.getDescriptor().getId() +",buffer=" + i + ",record=" + j;
        writer.append(Record.create(key, key));
      }
      writer.commit();
      writer.close() ;
    }
  }
}