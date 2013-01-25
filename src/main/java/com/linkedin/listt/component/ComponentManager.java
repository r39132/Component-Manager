package com.linkedin.listt.component;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComponentManager {
  /************************************************************************
   * CONSTANTS
   ************************************************************************/
  private static final ComponentManager INSTANCE = new ComponentManager();
  private static final Logger LOGGER = LoggerFactory.getLogger(ComponentManager.class);
  private static final String ERROR_MSG_TEMPLATE = "You are not allowed to call %s!";
  private static volatile boolean isInitialized = false;

  /************************************************************************
   * STATE
   ************************************************************************/
  private MBeanServer m_mbeanServer = null;
  private Map<String, Component> m_componentMap = new HashMap<String, Component>();
  // A very simple state machine uses the NextStep enum with the index below
  private volatile NextStep nextAllowedState = NextStep.INITIALIZE;

  /************************************************************************
   * INNER ENUM DEFINITION
   ************************************************************************/
  private enum NextStep {
    DEAD(null), STOP(DEAD), START(STOP), INITIALIZE(START);

    private NextStep m_ns;

    private NextStep(NextStep ns) {
      m_ns = ns;
    }

    public NextStep getNextState() {
      return m_ns;
    }
  }

  /************************************************************************
   * SINGLETON METHODS
   ************************************************************************/
  private ComponentManager() {
    // Register Mbeans
    m_mbeanServer = ManagementFactory.getPlatformMBeanServer();
  }

  public static ComponentManager getInstance() {
    return INSTANCE;
  }

  /************************************************************************
   * API METHODS
   ************************************************************************/
  public Component getComponent(String componentName) {
    // getComponent can only be called once the component has been INIT'd
    // and before it is
    // stopped
    if (!isInitialized || nextAllowedState == NextStep.DEAD) {
      throw new IllegalStateException(String.format(ERROR_MSG_TEMPLATE, "getMBeanInfo"));
    }

    return this.m_componentMap.get(componentName);
  }

  public MBeanInfo getMBeanInfo(String mBeanName) throws Throwable {
    // getMBeanInfo can only be called once the component has been INIT'd
    // and before it is
    // stopped
    if (!isInitialized || nextAllowedState == NextStep.DEAD) {
      throw new IllegalStateException(String.format(ERROR_MSG_TEMPLATE, "getMBeanInfo"));
    }

    return this.m_mbeanServer.getMBeanInfo(new ObjectName(mBeanName));
  }

  public Object getAttribute(String mBeanName, String attributeName) throws Throwable {
    // getAttribute can only be called once the component has been INIT'd
    // and before it is
    // stopped
    if (!isInitialized || nextAllowedState == NextStep.DEAD) {
      throw new IllegalStateException(String.format(ERROR_MSG_TEMPLATE, "getAttribute"));
    }

    return this.m_mbeanServer.getAttribute(new ObjectName(mBeanName), attributeName);
  }

  public synchronized void initialize() throws Throwable {
    // If Initialize is called more than once, just ignore the other calls
    // to initialize
    if (isInitialized) {
      return;
    }

    // Create and configure a reflections object
    Reflections reflections =
        new Reflections(
            new ConfigurationBuilder()
                .filterInputsBy(
                    new FilterBuilder().include(FilterBuilder.prefix("com.linkedin.listt")))
                .setUrls(ClasspathHelper.forPackage("com.linkedin.listt"))
                .setScanners(new SubTypesScanner()));

    // Now, use reflection to initialize all of the components
    Set<Class<? extends Component>> components = reflections.getSubTypesOf(Component.class);
    Iterator<Class<? extends Component>> iter = components.iterator();
    while (iter.hasNext()) {
      Class<? extends Component> clz = iter.next();
      Component component = (Component) clz.newInstance();
      LOGGER.info("Component Manager: initialization of component = " + component.getName()
          + " started!");
      component.init();
      LOGGER.info("Component Manager: initialization of component = " + component.getName()
          + " completed!");
      if (m_componentMap.containsKey(component.getName())) {
        throw new IllegalArgumentException(
            "Component Manager:initialize::component failed due to a conflicting name. "
                + component.getName() + " is already registered!");
      }

      m_componentMap.put(component.getName(), component);

      // If we have an exception here, just do not register the mbeans for
      // this
      // component and move on to the next component
      Map<ObjectName, Object> mBeanMap = component.getMBeans();
      if (mBeanMap != null) {
        Iterator<Entry<ObjectName, Object>> mbIter = mBeanMap.entrySet().iterator();
        while (mbIter.hasNext()) {
          try {
            Entry<ObjectName, Object> entry = mbIter.next();
            try {
              this.m_mbeanServer.unregisterMBean(entry.getKey());
            } catch (InstanceNotFoundException infe) {
              // do nothing
            }
            this.m_mbeanServer.registerMBean(entry.getValue(), entry.getKey());
            LOGGER.info("Component Manager: registering of component = " + component.getName()
                + " and mbean =" + entry.getKey() + " completed!");
          } catch (Throwable t) {
            LOGGER.error("Throwable encountered registering plaform mbean", t);
          }
        }
      }
    }

    // Move to the next state
    nextAllowedState = nextAllowedState.getNextState();
    isInitialized = true;
    return;
  }

  public synchronized void start() throws Throwable {
    // Ensure that we are permitted to call "START"
    if (NextStep.START != nextAllowedState) {
      throw new IllegalStateException(String.format(ERROR_MSG_TEMPLATE, "start"));
    }

    Iterator<Entry<String, Component>> it = m_componentMap.entrySet().iterator();
    while (it.hasNext()) {
      Entry<String, Component> entry = it.next();
      Component component = entry.getValue();
      LOGGER.info("Component Manager: start of component = " + component.getName() + " starting!");
      component.start();
      LOGGER.info("Component Manager: start of component = " + component.getName() + " completed!");
    }

    // Move to the next state
    nextAllowedState = nextAllowedState.getNextState();
    return;
  }

  public synchronized void shutdown() throws Throwable {
    // Ensure that we are permitted to call "START"
    if (NextStep.STOP != nextAllowedState) {
      throw new IllegalStateException(String.format(ERROR_MSG_TEMPLATE, "stop"));
    }

    Iterator<Entry<String, Component>> it = m_componentMap.entrySet().iterator();
    while (it.hasNext()) {
      Entry<String, Component> entry = it.next();
      try {
        Component component = entry.getValue();
        LOGGER.info("Component Manager: shutdown of component name = " + component.getName()
            + " starting!");
        component.shutdown();
        LOGGER.info("Component Manager: shutdown of component name = " + component.getName()
            + " completed!");
      } catch (Throwable t) {
        LOGGER.error("Throwable encountered shutting down component manager", t);
      }
    }
    // Move to the next state
    nextAllowedState = nextAllowedState.getNextState();
    return;

  }

}
