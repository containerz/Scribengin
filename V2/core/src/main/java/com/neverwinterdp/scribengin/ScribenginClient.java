package com.neverwinterdp.scribengin;

import static com.neverwinterdp.vm.tool.VMClusterBuilder.h1;

import java.util.Collections;
import java.util.List;

import com.neverwinterdp.registry.Node;
import com.neverwinterdp.registry.NodeCreateMode;
import com.neverwinterdp.registry.Registry;
import com.neverwinterdp.registry.RegistryException;
import com.neverwinterdp.registry.event.NodeEvent;
import com.neverwinterdp.registry.event.NodeWatcher;
import com.neverwinterdp.scribengin.dataflow.DataflowClient;
import com.neverwinterdp.scribengin.dataflow.DataflowDescriptor;
import com.neverwinterdp.scribengin.dataflow.DataflowLifecycleStatus;
import com.neverwinterdp.scribengin.dataflow.DataflowRegistry;
import com.neverwinterdp.scribengin.event.ScribenginShutdownEventListener;
import com.neverwinterdp.scribengin.event.ScribenginWaitingEventListener;
import com.neverwinterdp.scribengin.service.ScribenginService;
import com.neverwinterdp.scribengin.service.VMScribenginServiceApp;
import com.neverwinterdp.scribengin.service.VMScribenginServiceCommand;
import com.neverwinterdp.util.JSONSerializer;
import com.neverwinterdp.vm.VMConfig;
import com.neverwinterdp.vm.VMDescriptor;
import com.neverwinterdp.vm.client.VMClient;
import com.neverwinterdp.vm.command.Command;
import com.neverwinterdp.vm.command.CommandPayload;
import com.neverwinterdp.vm.command.CommandResult;

public class ScribenginClient {
  private VMClient vmClient;

  public ScribenginClient(Registry registry) {
    vmClient = new VMClient(registry);
  }
  
  public ScribenginClient(VMClient vmClient) {
    this.vmClient = vmClient;
  }

  public Registry getRegistry() { return this.vmClient.getRegistry(); }
  
  public VMClient getVMClient() { return this.vmClient ; }
  
  public VMDescriptor getScribenginMaster() throws RegistryException {
    Node node = vmClient.getRegistry().getRef(ScribenginService.LEADER_PATH);
    VMDescriptor descriptor = node.getDataAs(VMDescriptor.class);
    return descriptor;
  }
  
  public List<VMDescriptor> getScribenginMasters() throws RegistryException {
    Registry registry = vmClient.getRegistry();
    List<VMDescriptor> vmDescriptors = registry.getRefChildrenAs(ScribenginService.LEADER_PATH, VMDescriptor.class) ;
    return vmDescriptors;
  }
  
  public List<DataflowDescriptor> getRunningDataflowDescriptor() throws RegistryException {
    List<DataflowDescriptor> holder =
      vmClient.getRegistry().getChildrenAs(ScribenginService.DATAFLOWS_RUNNING_PATH, DataflowDescriptor.class) ;
    holder.removeAll(Collections.singleton(null));
    return holder;
  }
  
  public List<DataflowDescriptor> getHistoryDataflowDescriptor() throws RegistryException {
    List<DataflowDescriptor> holder = 
        vmClient.getRegistry().getChildrenAs(ScribenginService.DATAFLOWS_HISTORY_PATH, DataflowDescriptor.class) ;
    holder.removeAll(Collections.singleton(null));
    return holder;
  }
  
  public DataflowRegistry getRunningDataflowRegistry(String name) throws Exception {
    String dataflowPath = ScribenginService.DATAFLOWS_RUNNING_PATH + "/" + name;
    DataflowRegistry dataflowRegistry = new DataflowRegistry(getRegistry(), dataflowPath);
    return dataflowRegistry;
  }
  
  public DataflowRegistry getHistoryDataflowRegistry(String id) throws Exception {
    String dataflowPath = ScribenginService.DATAFLOWS_HISTORY_PATH + "/" + id;
    DataflowRegistry dataflowRegistry = new DataflowRegistry(getRegistry(), dataflowPath);
    return dataflowRegistry;
  }
  
  public ScribenginWaitingEventListener submit(String dataflowAppHome, String jsonDescriptor) throws Exception {
    DataflowDescriptor descriptor = JSONSerializer.INSTANCE.fromString(jsonDescriptor, DataflowDescriptor.class) ;
    return submit(dataflowAppHome, descriptor) ;
  }
  
  public ScribenginWaitingEventListener submit(DataflowDescriptor descriptor) throws Exception {
    return submit(null, descriptor) ;
  }
  
  public ScribenginWaitingEventListener submit(String localDataflowHome, DataflowDescriptor descriptor) throws Exception {
    if(localDataflowHome != null) {
      VMDescriptor vmMaster = getVMClient().getMasterVMDescriptor();
      VMConfig vmConfig = vmMaster.getVmConfig();
      String dataflowAppHome = vmConfig.getAppHome() + "/dataflows/" + descriptor.getName();
      descriptor.setDataflowAppHome(dataflowAppHome);
      getVMClient().uploadApp(localDataflowHome, dataflowAppHome);
    }
    h1("Submit the dataflow " + descriptor.getName());
    String name = descriptor.getName() ;
    VMClient vmClient = new VMClient(getRegistry());
    ScribenginWaitingEventListener waitingEventListener = new ScribenginWaitingEventListener(vmClient.getRegistry());
    waitingEventListener.waitDataflowLeader(format("Expect %s-master-1 as the leader", name), name,  format("%s-master-1", name));
    waitingEventListener.waitDataflowStatus("Expect dataflow init status", name, DataflowLifecycleStatus.INIT);
    waitingEventListener.waitDataflowStatus("Expect dataflow running status", name, DataflowLifecycleStatus.RUNNING);
    waitingEventListener.waitDataflowStatus("Expect dataflow  finish status", name, DataflowLifecycleStatus.FINISH);
   
    VMDescriptor scribenginMaster = getScribenginMaster();
    Command deployCmd = new VMScribenginServiceCommand.DataflowDeployCommand(descriptor) ;
    CommandResult<Boolean> result = (CommandResult<Boolean>)vmClient.execute(scribenginMaster, deployCmd, 35000);
    return waitingEventListener;
  }
  
  private String format(String tmpl, Object ... args) {
    return String.format(tmpl, args) ;
  }
  
  public VMDescriptor createVMScribenginMaster(VMClient vmClient, String name) throws Exception {
    VMConfig vmConfig = new VMConfig() ;
    vmConfig.
      setName(name).
      addRoles("scribengin-master").
      setRegistryConfig(vmClient.getRegistry().getRegistryConfig()).
      setVmApplication(VMScribenginServiceApp.class.getName());
    vmClient.configureEnvironment(vmConfig);
    VMDescriptor vmDescriptor = vmClient.allocate(vmConfig);
    return vmDescriptor;
  }
  
  public DataflowClient getDataflowClient(String dataflowName) throws Exception {
    String dataflowPath = ScribenginService.getDataflowPath(dataflowName);
    DataflowClient dataflowClient = new DataflowClient(this, dataflowPath);
    return dataflowClient ;
  }
  
  public void shutdown() throws Exception {
    h1("Shutdow the scribengin");
    Registry registry = vmClient.getRegistry();
    registry.create(ScribenginShutdownEventListener.EVENT_PATH, true, NodeCreateMode.PERSISTENT);
  }
  
  public CommandResult<?> execute(VMDescriptor vmDescriptor, Command command) throws RegistryException, Exception {
    return execute(vmDescriptor, command, 30000);
  }
  
  public CommandResult<?> execute(VMDescriptor vmDescriptor, Command command, long timeout) throws RegistryException, Exception {
    CommandPayload payload = new CommandPayload(command, null) ;
    Registry registry = vmClient.getRegistry();
    Node node = registry.create(vmDescriptor.getStoredPath() + "/commands/command-", payload, NodeCreateMode.EPHEMERAL_SEQUENTIAL);
    CommandReponseWatcher responseWatcher = new CommandReponseWatcher();
    node.watch(responseWatcher);
    return responseWatcher.waitForResult(timeout);
  }
  
  public class CommandReponseWatcher extends NodeWatcher {
    private CommandResult<?> result ;
    private Exception error ;
    
    @Override
    public void onEvent(NodeEvent event) {
      String path = event.getPath();
      try {
        Registry registry = vmClient.getRegistry();
        CommandPayload payload = registry.getDataAs(path, CommandPayload.class) ;
        result = payload.getResult() ;
        registry.delete(path);
        synchronized(this) {
          notify();
        }
      } catch(Exception e) {
        error = e ;
      }
    }
    
    public CommandResult<?> waitForResult(long timeout) throws Exception {
      if(result == null) {
        synchronized(this) {
          wait(timeout);
        }
      }
      if(error != null) throw error;
      return result ;
    }
  }
}
