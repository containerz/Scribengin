package com.neverwinterdp.scribengin.stream.sink;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.lang.StringUtils;

import java.util.Map;
import java.util.Properties;

public class S3SinkConfig {

  private final PropertiesConfiguration properties;

  /**
   * Exposed for testability
   * 
   * @param properties
   */
  public S3SinkConfig(String configProperty) {
    Properties systemProperties = System.getProperties();
    systemProperties.getProperty("config");
    try {
      properties = new PropertiesConfiguration(configProperty);

    } catch (ConfigurationException e) {
      throw new RuntimeException("Error loading configuration from " + configProperty);
    }

    for (final Map.Entry<Object, Object> entry : systemProperties.entrySet()) {
      properties.setProperty(entry.getKey().toString(), entry.getValue());
    }
  }

  public String getBucketName() {
    return getString("bucketName");
  }
  
  public String getRegionName() {
    return getString("regionName");
  }

  public String getLocalTmpDir() {
    return getString("localTmpDir");
  }
  public int getChunkSize() {
    return getInt("chunkSize");
  }
  public int getMemoryMaxBufferSize() {
    return getInt("memoryBuffer.maxBufferSize");
  }
 
  public int getMemoryMaxBufferingTime() {
    return getInt("memoryBuffer.maxBufferingTime");
  }
  
  public int getMemoryMaxTuples() {
    return getInt("memoryBuffer.maxTuples");
  }
  
  public int getDiskMaxBufferSize() {
    return getInt("diskBuffer.maxBufferSize");
  }
 
  public int getDiskMaxBufferingTime() {
    return getInt("diskBuffer.maxBufferingTime");
  }
  
  public int getDiskMaxTuples() {
    return getInt("diskBuffer.maxTuples");
  }

  
  public int getOffsetPerPartition() {
    return getInt("partitionner.offsetPerPartition");
  }
  
  
  private void checkProperty(String name) {
    if (!properties.containsKey(name)) {
      throw new RuntimeException("Failed to find required configuration option '" + name + "'.");
    }
  }

  private String getString(String name) {
    checkProperty(name);
    return properties.getString(name);
  }

  private int getInt(String name) {
    checkProperty(name);
    return properties.getInt(name);
  }

  private long getLong(String name) {
    return properties.getLong(name);
  }

  private String[] getStringArray(String name) {
    return properties.getStringArray(name);
  }
}
