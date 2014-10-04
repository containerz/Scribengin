package com.neverwinterdp.scribengin.cluster;

import org.apache.log4j.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.neverwinterdp.server.Server;
import com.neverwinterdp.server.shell.Shell;

public class ScribeMasterClusterYarnTest {
  static {
    System.setProperty("log4j.configuration", "file:src/app/config/log4j.properties") ;
  }
  
  private static ScribeConsumerClusterTestHelper helper = new ScribeConsumerClusterTestHelper();
  static int numOfMessages = 100 ;
  private static final Logger LOG = Logger.getLogger(ScribeConsumerClusterTest.class.getName());
  private static Server scribeMaster;
  
  @BeforeClass
  static public void setup() throws Exception {
    helper.setup();
  }

  @AfterClass
  static public void teardown() throws Exception {
    helper.teardown();
    try{
      scribeMaster.destroy();
    } catch(Exception e){}
  }
  
  
  @Test
  public void TestScribeMasterClusterDistributed() throws InterruptedException{
    
    //Bring up scribeMaster
    scribeMaster = Server.create("-Pserver.name=scribemaster", "-Pserver.roles=scribemaster");
    Shell shell = new Shell() ;
    shell.getShellContext().connect();
    shell.execute("module list --type available");
    
    String installScript ="module install " + 
        " -Pmodule.data.drop=true" +
        " -Pscribemaster:topics="+ helper.getTopic() +
        " -Pscribemaster:brokerList=127.0.0.1:9092" +
        " -Pscribemaster:hdfsPath="+helper.getHadoopConnection()+
        " -Pscribemaster:cleanStart=True"+
        " -Pscribemaster:mode=yarn"+
        " --member-role scribemaster --autostart --module ScribeMaster \n";
    shell.executeScript(installScript);
    Thread.sleep(2000);
    
    LOG.info("Creating kafka data");
    //Create kafka data
    helper.createKafkaData(0);
      
    //Wait for consumption
    Thread.sleep(10000);
    //Ensure messages 0-100 were consumed
    LOG.info("Asserting data is correct");
    helper.assertHDFSmatchesKafka(0,helper.getHadoopConnection());
  }
}
