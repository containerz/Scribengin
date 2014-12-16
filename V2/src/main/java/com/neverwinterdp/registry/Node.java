package com.neverwinterdp.registry;

import java.util.List;

import com.neverwinterdp.registry.election.LeaderElection;
import com.neverwinterdp.registry.lock.Lock;
import com.neverwinterdp.util.JSONSerializer;

public class Node {
  private Registry  registry ;
  private String path  ;
  
  public Node(Registry registry, String path) {
    this.registry = registry ;
    this.path = path ;
  }

  public Registry getRegistry() { return this.registry ; }
  
  public String getPath() { return path; }

  public String getParentPath() {
    if(path.length() == 1 && path.equals("/")) return null ;
    int idx = path.lastIndexOf('/') ;
    return path.substring(0, idx) ; 
  }

  public boolean exists() throws RegistryException {
    return registry.exists(path) ;
  }
  
  public byte[] getData() throws RegistryException { 
    return registry.getData(path); 
  }
  
  public <T> T getData(Class<T> type) throws RegistryException { 
    byte[] data = getData();
    return JSONSerializer.INSTANCE.fromBytes(data, type);
  }

  public void setData(byte[] data) throws RegistryException {
    registry.setData(path, data);
  }
  
  public <T> void setData(T data) throws RegistryException {
    registry.setData(path, data);
  }

  public void delete() throws RegistryException {
    registry.delete(path);
  }

  public Lock getLock(String name) { return new Lock(registry, path, name) ; }

  public LeaderElection getLeaderElection() { return new LeaderElection(registry, path) ; }

  public void watch(NodeWatcher watcher) throws RegistryException {
    registry.watch(path, watcher);
  }
  
  public void watchChildren(NodeWatcher watcher) throws RegistryException {
    registry.watchChildren(path, watcher);
  }

  public void unwatch() throws RegistryException {
    throw new RegistryException(ErrorCode.Unknown, "Zookeeper does not support this method");
  }
  
  public boolean hasChild(String name) throws RegistryException {
    return registry.exists(path + "/" + name) ;
  }
  
  public Node getChild(String name) throws RegistryException {
    return new Node(registry, path + "/" + name) ;
  }
  
  public List<String> getChildren() throws RegistryException {
    return registry.getChildren(path) ;
  }
  
  public Node createChild(String name, NodeCreateMode mode) throws RegistryException {
    Node child = registry.create(path + "/" + name, mode);
    return child;
  }
  
  public Node createChild(String name, byte[] data, NodeCreateMode mode) throws RegistryException {
    Node child = registry.create(path + "/" + name, data, mode);
    return child;
  }
  
  public <T> Node createChild(String name, T data, NodeCreateMode mode) throws RegistryException {
    Node child = registry.create(path + "/" + name, data, mode);
    return child;
  }
}
