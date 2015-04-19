from tabulate import tabulate
from multiprocessing import Pool
from subprocess import call
import os, socket



#This function is outside the ServerSet class
#because otherwise it wouldn't be pickleable 
#http://stackoverflow.com/questions/1816958/cant-pickle-type-instancemethod-when-using-pythons-multiprocessing-pool-ma
def getReportOnServer(server):
  omitStoppedProcesses = ["scribengin-master-*", "vm-master-*"]
  result = []
  serverReportDict = server.getReportDict()
  if serverReportDict is not None and serverReportDict["Hostname"] :
    result.append([serverReportDict["Role"], serverReportDict["Hostname"], "", "", "",""])
    procs = server.getProcesses()
    for process in procs:
      procDicts = server.getProcess(process).getReportDict()
      for procDict in procDicts:
        if not (procDict["Status"] == "None" and procDict["ProcessIdentifier"] in omitStoppedProcesses):
          result.append(["","",procDict["ProcessIdentifier"], procDict["processID"], procDict["HomeDir"], procDict["Status"]])
  return result

class ServerSet(object):
  def __init__(self, name, numProcessesForStatus=30):
    self.role = name 
    self.servers = []
    self.paramDict = {}
    self.numProcessesForStatus = numProcessesForStatus
    
  def clear(self):
    self.servers = []
  
  def printTitle(self, message):
    print "***********************************************************************"
    print message 
    print "***********************************************************************"  
  
  def addServer(self, server):
    self.servers.append(server)
  
  def sshExecute(self, command):
    for server in self.servers :
      server.sshExecute(command)
  
  def sync(self):
    print socket.gethostname()
    for server in self.servers :
      if server.getHostname() != socket.gethostname():
        self.printTitle("Sync data with " + server.getHostname())
        command = "rsync -a -r -c -P --delete --ignore-errors /opt/ " + server.user +"@"+ server.getHostname() + ":/opt"
        os.system(command)
    
  def startProcessOnHost(self, processName, hostname):
    for server in self.servers :
      if server.getHostname() == hostname:
        server.startProcess(processName, self.paramDict, setupClusterEnv = False)
    
  def cleanProcessOnHost(self, processName, hostname):
    for server in self.servers :
      if server.getHostname() == hostname:
        server.cleanProcess(processName)
  
  def shutdownProcessOnHost(self, processName, hostname):
    for server in self.servers :
      if server.getHostname() == hostname:
        server.shutdownProcess(processName)
  
  def killProcessOnHost(self, processName, hostname):
    for server in self.servers :
      if server.getHostname() == hostname:
        server.killProcess(processName)
  
  def startProcess(self, processNames, setupClusterEnv = True):
    for processName in processNames.split(","):
      for server in self.servers :
        server.startProcess(processName, self.paramDict, setupClusterEnv)
    
  def cleanProcess(self, processNames):
    for processName in processNames.split(","):
      for server in self.servers : 
        server.cleanProcess(processName)
  
  def shutdownProcess(self, processNames):
    for processName in processNames.split(","):
      for server in self.servers :
        server.shutdownProcess(processName)
  
  def killProcess(self, processNames):
    for processName in processNames.split(","):
      for server in self.servers :
        server.killProcess(processName)
    
  def isProcessRunning(self, processName):
    for server in self.servers :
      process = server.getProcess(processName)
      if process is None:
        pass
      elif process.isRunning():
        return True
    return False
  
  def isProcessRunningOnHost(self, processName, hostname):
    for server in self.servers :
      if server.getHostname() == hostname:
        return server.getProcess(processName).isRunning()
    return False
        
  def getNumServers(self):
    return len(self.servers)
  
  def getServersByHostname(self, hostnames):
    serverSet = ServerSet("subset")
    serverSet.paramDict = self.paramDict
    for server in self.servers :
      if(server.getHostname().strip() in hostnames) :
        serverSet.addServer(server)
    return serverSet
  
  def getServersByRole(self, role):
    """
    Returns a subset of the ServerSet that has the role passed in
    """
    serverSet = ServerSet(role)
    for server in self.servers :
      if(server.getRole() == role) :
        serverSet.addServer(server)
    return serverSet
  
  def startVmMaster(self):
    hadoopMasterServers = self.getServersByRole("hadoop-worker")
    if hadoopMasterServers.servers:
      return self.startProcessOnHost("vmmaster", hadoopMasterServers.servers[0].getHostname())
  
  def startScribengin(self):
    hadoopMasterServers = self.getServersByRole("hadoop-worker")
    if hadoopMasterServers.servers:
      return self.startProcessOnHost("scribengin", hadoopMasterServers.servers[0].getHostname())
  
  def startZookeeper(self):
    return self.startProcess("zookeeper")
  
  def startKafka(self):
    return self.startProcess("kafka")
  
  def cleanHadoopDataAtFirst(self):
    serverSet = self.getServersByRole("hadoop-worker")
    if not serverSet.servers[0].getProcess("datanode").isDataDirExists():
      self.cleanHadoopMaster()
      self.cleanHadoopWorker()
    
  def startNameNode(self):
    return self.startProcess("namenode", setupClusterEnv = True)
  
  def startSecondaryNameNode(self):
    return self.startProcess("secondarynamenode", setupClusterEnv = False)
  
  def startResourceManager(self):
    return self.startProcess("resourcemanager", setupClusterEnv = False)
  
  def startDataNode(self):
    return self.startProcess("datanode", setupClusterEnv = True)
  
  def startNodeManager(self):
    return self.startProcess("nodemanager", setupClusterEnv = False)
  
  def startHadoopMaster(self):
    return self.startProcess("namenode,secondarynamenode,resourcemanager")
  
  def startHadoopWorker(self):
    return self.startProcess("datanode,nodemanager")
  
  def shutdownVmMaster(self):
    hadoopMasterServers = self.getServersByRole("hadoop-worker")
    if hadoopMasterServers.servers:
      return self.shutdownProcessOnHost("vmmaster", hadoopMasterServers.servers[0].getHostname())
  
  def shutdownScribengin(self):
    hadoopMasterServers = self.getServersByRole("hadoop-worker")
    if hadoopMasterServers.servers:
      return self.shutdownProcessOnHost("scribengin", hadoopMasterServers.servers[0].getHostname())
  
  def shutdownZookeeper(self):
    return self.shutdownProcess("zookeeper")
  
  def shutdownKafka(self):
    return self.shutdownProcess("kafka")
  
  def shutdownNameNode(self):
    return self.shutdownProcess("namenode")
  
  def shutdownSecondaryNameNode(self):
    return self.shutdownProcess("secondarynamenode")
  
  def shutdownResourceManager(self):
    return self.shutdownProcess("resourcemanager")
  
  def shutdownDataNode(self):
    return self.shutdownProcess("datanode")
  
  def shutdownNodeManager(self):
    return self.shutdownProcess("nodemanager")
  
  def shutdownHadoopMaster(self):
    return self.shutdownProcess("namenode,secondarynamenode,resourcemanager")
  
  def shutdownHadoopWorker(self):
    return self.shutdownProcess("datanode,nodemanager")
  
  def killScribengin(self):
    return self.killProcess("scribengin")
  
  def killVmMaster(self):
    return self.killProcess("vmmaster")
  
  def killZookeeper(self):
    return self.killProcess("zookeeper")
  
  def killKafka(self):
    return self.killProcess("kafka")
  
  def killNameNode(self):
    return self.killProcess("namenode")
  
  def killSecondaryNameNode(self):
    return self.killProcess("secondarynamenode")
  
  def killResourceManager(self):
    return self.killProcess("resourcemanager")
  
  def killDataNode(self):
    return self.killProcess("datanode")
  
  def killNodeManager(self):
    return self.killProcess("nodemanager")
  
  def killHadoopMaster(self):
    return self.killProcess("namenode,secondarynamenode,resourcemanager")
  
  def killHadoopWorker(self):
    return self.killProcess("datanode,nodemanager")  
  
  def cleanKafka(self):
    return self.cleanProcess("kafka")
  
  def cleanZookeeper(self):
    return self.cleanProcess("zookeeper")
  
  def cleanHadoopMaster(self):
    return self.cleanProcess("namenode")
  
  def cleanHadoopWorker(self):
    return self.cleanProcess("datanode")
  
  
  def getReport(self):
    serverReport = []
    asyncresults = []
    pool = Pool(processes=self.numProcessesForStatus)
    sorted_servers = sorted(self.servers, key=lambda server: server.role)
    for server in sorted_servers:
      asyncresults.append( pool.apply_async(getReportOnServer, [server])) 
    
    for async in asyncresults:
      result = async.get(timeout=30)
      if result: 
        for row in result:
          serverReport.append(row)

    headers = ["Role", "Hostname", "ProcessIdentifier", "ProcessID", "HomeDir", "Status"]
    pool.close()
    pool.join()
    return tabulate(serverReport, headers=headers)
 
  def report(self) :
    print self.getReport()
