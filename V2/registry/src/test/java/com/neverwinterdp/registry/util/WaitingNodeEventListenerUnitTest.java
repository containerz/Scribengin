package com.neverwinterdp.registry.util;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.mycila.guice.ext.closeable.CloseableInjector;
import com.mycila.guice.ext.closeable.CloseableModule;
import com.mycila.guice.ext.jsr250.Jsr250Module;
import com.neverwinterdp.module.AppModule;
import com.neverwinterdp.registry.Node;
import com.neverwinterdp.registry.Registry;
import com.neverwinterdp.registry.RegistryConfig;
import com.neverwinterdp.registry.event.NodeEvent;
import com.neverwinterdp.registry.event.NodeEventMatcher;
import com.neverwinterdp.registry.event.WaitingNodeEventListener;
import com.neverwinterdp.registry.zk.RegistryImpl;
import com.neverwinterdp.util.FileUtil;
import com.neverwinterdp.zk.tool.server.EmbededZKServer;

public class WaitingNodeEventListenerUnitTest {
  static {
    System.setProperty("log4j.configuration", "file:src/test/resources/test-log4j.properties") ;
  }
  
  static public String EVENTS_PATH  = "/events" ;
  
  static private EmbededZKServer zkServerLauncher ;
  
  private Injector container ;
  private Registry registry ;
  
  @BeforeClass
  static public void startServer() throws Exception {
    FileUtil.removeIfExist("./build/data", false);
    zkServerLauncher = new EmbededZKServer("./build/data/zookeeper") ;
    zkServerLauncher.start();
  }
  
  static public void stopServer() throws Exception {
    zkServerLauncher.shutdown();
  }
  
  @Before
  public void setup() throws Exception {
    AppModule module = new AppModule(new HashMap<String, String>()) {
      @Override
      protected void configure(Map<String, String> properties) {
        bindInstance(RegistryConfig.class, RegistryConfig.getDefault());
        bindType(Registry.class, RegistryImpl.class);
      }
    };
    container = 
      Guice.createInjector(Stage.PRODUCTION, new CloseableModule(), new Jsr250Module(), module);
    registry = container.getInstance(Registry.class);
  }
  
  @After
  public void teardown() throws Exception {
    registry.disconnect();
    container.getInstance(CloseableInjector.class).close();
  }

  @Test
  public void testListener() throws Exception {
    WaitingNodeEventListener listener = new WaitingNodeEventListener(registry);
    listener.add(EVENTS_PATH, NodeEvent.Type.CREATE);
    listener.add(EVENTS_PATH + "/hello", new NodeEvent.Type[] {NodeEvent.Type.CREATE, NodeEvent.Type.MODIFY});
    listener.add(EVENTS_PATH + "/hello", new HelloBean("hello"));
    listener.add(EVENTS_PATH + "/dummy", new NodeEventMatcher() {
      @Override
      public boolean matches(Node node, NodeEvent event) throws Exception {
        return NodeEvent.Type.CREATE == event.getType();
      }
      
    });
    registry.createIfNotExist(EVENTS_PATH);
    Node hello = registry.createIfNotExist(EVENTS_PATH + "/hello");
    hello.setData(new HelloBean("hello"));
    
    Thread.sleep(100);
    hello.setData(new HelloBean("hello(update)"));

    Node dummy = registry.createIfNotExist(EVENTS_PATH + "/dummy");
    
    listener.waitForEvents(1000);
    
    Assert.assertEquals(listener.getDetectNodeEventCount(), listener.getWaitingNodeEventCount());
    Thread.sleep(100);
    System.err.println("Done!!!!!!!!!!!!!!!!!!!!!!!!!") ;
  }
  
  
  
  static public class HelloBean {
    private String hello ;
    
    public HelloBean() {
      
    }
    
    public HelloBean(String hello) {
      this.hello = hello ;
    }

    public String getHello() { return hello; }
    public void setHello(String hello) { this.hello = hello; }
    
    @Override
    public boolean equals(Object obj) {
      if(obj == null) return false ;
      HelloBean other = (HelloBean) obj;
      return hello.equals(other.hello) ;
    }
  }
}