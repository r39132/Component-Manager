package com.linkedin.listt.component;

import java.util.Map;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

public interface Component {
  public void init() throws Throwable;

  public void start() throws Throwable;

  public void shutdown() throws Throwable;

  public Map<ObjectName, Object> getMBeans() throws MalformedObjectNameException;

  public String getName();
}
