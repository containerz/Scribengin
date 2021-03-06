package com.neverwinterdp.vm.client;

import static com.neverwinterdp.vm.tool.VMClusterBuilder.h1;

import java.util.List;

import com.mycila.jmx.annotation.JmxBean;
import com.neverwinterdp.registry.Node;
import com.neverwinterdp.registry.NodeCreateMode;
import com.neverwinterdp.registry.Registry;
import com.neverwinterdp.registry.RegistryException;
import com.neverwinterdp.registry.event.NodeEvent;
import com.neverwinterdp.registry.event.NodeWatcher;
import com.neverwinterdp.vm.VMConfig;
import com.neverwinterdp.vm.VMDescriptor;
import com.neverwinterdp.vm.command.Command;
import com.neverwinterdp.vm.command.CommandPayload;
import com.neverwinterdp.vm.command.CommandResult;
import com.neverwinterdp.vm.command.VMCommand;
import com.neverwinterdp.vm.event.VMShutdownEventListener;
import com.neverwinterdp.vm.service.VMService;
import com.neverwinterdp.vm.service.VMServiceCommand;

@JmxBean("role=vm-client, type=VMClient, name=VMClient")
public class VMClient {
  private Registry registry;

  public VMClient(Registry registry) {
    this.registry = registry;
  }
  
  public Registry getRegistry() { return this.registry ; }
  
  public List<VMDescriptor> getRunningVMDescriptors() throws RegistryException {
    return registry.getChildrenAs(VMService.ALLOCATED_PATH, VMDescriptor.class) ;
  }
  
  public List<VMDescriptor> getHistoryVMDescriptors() throws RegistryException {
    return registry.getChildrenAs(VMService.HISTORY_PATH, VMDescriptor.class) ;
  }
  
  public VMDescriptor getMasterVMDescriptor() throws RegistryException { 
    Node vmNode = registry.getRef(VMService.LEADER_PATH);
    return vmNode.getDataAs(VMDescriptor.class);
  }
  
  public void shutdown() throws Exception {
    h1("Shutdow the vm masters");
    registry.create(VMShutdownEventListener.EVENT_PATH, true, NodeCreateMode.PERSISTENT);
  }
  
  public CommandResult<?> execute(VMDescriptor vmDescriptor, Command command) throws RegistryException, Exception {
    return execute(vmDescriptor, command, 30000);
  }
  
  public CommandResult<?> execute(VMDescriptor vmDescriptor, Command command, long timeout) throws RegistryException, Exception {
    CommandPayload payload = new CommandPayload(command, null) ;
    Node node = registry.create(vmDescriptor.getStoredPath() + "/commands/command-", payload, NodeCreateMode.EPHEMERAL_SEQUENTIAL);
    CommandReponseWatcher responseWatcher = new CommandReponseWatcher();
    node.watch(responseWatcher);
    return responseWatcher.waitForResult(timeout);
  }
  
  public void execute(VMDescriptor vmDescriptor, Command command, CommandCallback callback) {
  }
  
  public VMDescriptor allocate(VMConfig vmConfig) throws Exception {
    VMDescriptor masterVMDescriptor = getMasterVMDescriptor();
    CommandResult<VMDescriptor> result = 
        (CommandResult<VMDescriptor>) execute(masterVMDescriptor, new VMServiceCommand.Allocate(vmConfig));
    if(result.getErrorStacktrace() != null) {
      registry.get("/").dump(System.err);
      throw new Exception(result.getErrorStacktrace());
    }
    return result.getResult();
  }
  
  public VMDescriptor allocate(String localAppHome, VMConfig vmConfig) throws Exception {
    return allocate(vmConfig);
  }
  
  public boolean shutdown(VMDescriptor vmDescriptor) throws Exception {
    CommandResult<?> result = execute(vmDescriptor, new VMCommand.Shutdown());
    return result.getResultAs(Boolean.class);
  }
  
  public void uploadApp(String localAppHome, String appHome) throws Exception {
  }
  
  public void createVMMaster(String name) throws Exception {
    throw new RuntimeException("This method need to override") ;
  }
  
  public void configureEnvironment(VMConfig vmConfig) {
    throw new RuntimeException("This method need to override") ;
  }
  
  public class CommandReponseWatcher extends NodeWatcher {
    private CommandResult<?> result ;
    private Exception error ;
    
    @Override
    public void onEvent(NodeEvent event) {
      String path = event.getPath();
      try {
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