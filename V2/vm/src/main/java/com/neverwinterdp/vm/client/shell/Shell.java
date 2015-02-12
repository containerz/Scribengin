package com.neverwinterdp.vm.client.shell;

import java.util.HashMap;
import java.util.Map;

import com.neverwinterdp.vm.client.VMClient;

public class Shell {
  protected Console console ;
  protected VMClient vmClient ;
  protected Map<String, Command> commands = new HashMap<String, Command>() ;
  protected Map<String, Object> attributes = new HashMap<String, Object>();
  
  public Shell(VMClient vmClient) {
    this(vmClient, new Console());
  }
  
  public Shell(VMClient vmClient, Console console){
    this.console = console ;
    this.vmClient = vmClient;
    add("registry", new RegistryCommand());
    add("vm", new VMCommand());
  }
  
  public Console console() { return this.console ; }
  
  public VMClient getVMClient() { return this.vmClient ; }
  
  public void add(String name, Command command) {
    commands.put(name, command);
  }
  
  public Object attribute(String name) {
    return attributes.get(name);
  }
  
  public void attribute(String name, Object attr) {
    attributes.put(name, attr);
  }
  
  public <T> T attribute(Class<T> type) {
    return (T) attributes.get(type.getName());
  }
  
  public <T> void attribute(Class<T> type, T attr) {
    attributes.put(type.getName(), attr);
  }
  
  public void execute(String cmdLine) throws Exception {
    execute(new CommandInput(cmdLine, true));
  }
  
  public void execute(String[] args) throws Exception {
    execute(new CommandInput(args, true));
  }
  
  void execute(CommandInput cmdInput) throws Exception {
    Command command = commands.get(cmdInput.getCommand());
    if(command == null) {
      throw new Exception("Unkown command " + cmdInput.getCommand()) ;
    }
    command.execute(this, cmdInput);
  }
}