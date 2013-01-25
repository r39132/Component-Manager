package com.linkedin.listt.component;

import java.util.HashMap;
import java.util.Map;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestComponentManager {
  /************************************************************************
   * CONSTANT
   ************************************************************************/
  private static final String COMPONENT_NAME =
      "com.linkedin.listt.component.TestComponentManager$DummyComponent";
  private static final String MBEAN_OBJECT_NAME = "com.linkedin.listt.component:type=DummyConfig";
  private static final String MBEAN_COUNT_ATTRIBUTE_NAME = "Count";
  private static final int NEW_COUNT = 111;
  private static final Logger LOGGER = LoggerFactory.getLogger(TestComponentManager.class);

  /************************************************************************
   * CTOR
   ************************************************************************/
  public TestComponentManager() {

  }

  /************************************************************************
   * TESTNG Methods
   ************************************************************************/
  @Test
  public void test() throws Throwable {
    // Initialize the components
    ComponentManager.getInstance().initialize();

    // Start the components
    ComponentManager.getInstance().start();

    // Get the component
    Component component = ComponentManager.getInstance().getComponent(COMPONENT_NAME);
    Assert.assertNotNull(component);

    // Get the MBean
    MBeanInfo mbeanInfo = ComponentManager.getInstance().getMBeanInfo(MBEAN_OBJECT_NAME);
    Assert.assertNotNull(mbeanInfo);

    MBeanAttributeInfo[] mbaiArray = mbeanInfo.getAttributes();
    Assert.assertNotNull(mbaiArray);
    boolean foundCountAttribute = false;
    for (MBeanAttributeInfo info : mbaiArray) {
      if (info.getName().equals(MBEAN_COUNT_ATTRIBUTE_NAME)) {
        foundCountAttribute = true;
      }
    }
    Assert.assertTrue(foundCountAttribute);

    Object o =
        ComponentManager.getInstance().getAttribute(MBEAN_OBJECT_NAME, MBEAN_COUNT_ATTRIBUTE_NAME);
    Assert.assertTrue(o instanceof Integer);
    Assert.assertEquals(((Integer) o).intValue(), NEW_COUNT);

    // Stop the components
    ComponentManager.getInstance().shutdown();

  }

  /************************************************************************
   * INNER CLASSES
   ************************************************************************/
  public static interface DummyConfigMBean {
    public int getCount();

    public void setCount(int count);

  }

  public static class DummyConfig implements DummyConfigMBean {
    private int m_count;

    @Override
    public int getCount() {
      return this.m_count;
    }

    @Override
    public void setCount(int count) {
      this.m_count = count;
    }

  }

  public static class DummyComponent implements Component {
    public DummyConfigMBean m_mBean;

    public DummyComponent() {
      m_mBean = new DummyConfig();
    }

    @Override
    public void init() {}

    @Override
    public void start() throws Throwable {
      m_mBean.setCount(NEW_COUNT);
    }

    @Override
    public void shutdown() {
      m_mBean.setCount(-1);
    }

    @Override
    public Map<ObjectName, Object> getMBeans() throws MalformedObjectNameException {
      Map<ObjectName, Object> mbeanMap = new HashMap<ObjectName, Object>();
      ObjectName name = new ObjectName(MBEAN_OBJECT_NAME);
      mbeanMap.put(name, m_mBean);
      LOGGER.info("ON = " + name);
      return mbeanMap;
    }

    @Override
    public String getName() {
      return this.getClass().getName();
    }
  }
}
