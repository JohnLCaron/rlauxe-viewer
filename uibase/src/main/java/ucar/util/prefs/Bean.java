/*
 * Copyright (c) 1998-2021 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.util.prefs;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

class Bean {
  private final Object o; // the wrapped object
  private BeanParser p; // the bean parser (shared for all beans of same class)

  // wrap an object in a Bean
  public Bean(Object o) {
    this.o = o;
  }

  // create a bean from an XML element
  public Bean(org.xml.sax.Attributes atts)
      throws ClassNotFoundException, InstantiationException, IllegalAccessException {
    String className = atts.getValue("class");
    Class<?> clazz = Class.forName(className);
    if (clazz == Float.class) {
      o = Float.valueOf(atts.getValue("value"));
    } else if (clazz == Double.class) {
      o = Double.valueOf(atts.getValue("value"));
    } else if (clazz == Integer.class) {
      o = Integer.valueOf(atts.getValue("value"));
    } else if (clazz == Long.class) {
    o = Long.valueOf(atts.getValue("value"));
    } else {
      try {
        o = clazz.getDeclaredConstructor().newInstance();
        p = BeanParser.getParser(clazz);
        p.readProperties(o, atts);
      } catch (InvocationTargetException | NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
  }

  // write XML using the bean properties of the contained object
  public void writeProperties(PrintWriter out) {
    if (p == null) {
      p = BeanParser.getParser(o.getClass());
    }
    p.writeProperties(o, out);
  }

  // get the wrapped object
  public Object getObject() {
    return o;
  }

  public Class<?> getBeanClass() {
    return o.getClass();
  }

  static class Collection {
    private final java.util.Collection<Object> collect; // the underlying collection
    private Class<?> beanClass; // the class of the beans in the collection
    private BeanParser p; // the bean parser (shared for all beans of same class)

    // wrap a collection in a bean
    Collection(java.util.Collection<?> collect) {
      this.collect = new ArrayList<>(collect); // generics, go figure!
      if (!collect.isEmpty()) {
        Iterator<?> iter = collect.iterator();
        beanClass = iter.next().getClass();
        p = BeanParser.getParser(beanClass);
      }
    }

    // create a bean collection from an XML element
    Collection(org.xml.sax.Attributes atts) throws ClassNotFoundException { // , InstantiationException,
                                                                            // IllegalAccessException {
      String className = atts.getValue("class");
      beanClass = Class.forName(className);
      p = BeanParser.getParser(beanClass);
      collect = new ArrayList<>();
    }

    // write XML using the bean properties of the specified object
    public void writeProperties(PrintWriter out, Object thiso) {
      p.writeProperties(thiso, out);
    }

    // get the underlying java.util.Collection
    public java.util.Collection<?> getCollection() {
      return collect;
    }

    public Class<?> getBeanClass() {
      return beanClass;
    }

    // write XML using the bean properties of the contained object
    public Object readProperties(org.xml.sax.Attributes atts) throws InstantiationException, IllegalAccessException {
      try {
        Object o = beanClass.getDeclaredConstructor().newInstance();
        p.readProperties(o, atts);
        collect.add(o);
        return o;
      } catch (InvocationTargetException | NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static class BeanParser {
    private static final boolean debugBean = false;
    private static final Map<Class<?>, BeanParser> parsers = new HashMap<>();

    static BeanParser getParser(Class<?> beanClass) {
      BeanParser parser;
      if (null == (parser = parsers.get(beanClass))) {
        parser = new BeanParser(beanClass);
        parsers.put(beanClass, parser);
      }
      return parser;
    }

    private final Map<String, PropertyDescriptor> properties = new TreeMap<>();
    private final Object[] args = new Object[1]; // why instance variable?

    BeanParser(Class<?> beanClass) {

      // get bean info
      BeanInfo info;
      try {
        info = Introspector.getBeanInfo(beanClass, Object.class);
      } catch (IntrospectionException e) {
        e.printStackTrace();
        return;
      }

      if (debugBean)
        System.out.println("Bean " + beanClass.getName());

      // properties must have read and write method
      PropertyDescriptor[] pds = info.getPropertyDescriptors();
      for (PropertyDescriptor pd : pds) {
        if ((pd.getReadMethod() != null) && (pd.getWriteMethod() != null)) {
          properties.put(pd.getName(), pd);
          if (debugBean) {
            System.out.println(" property " + pd.getName());
          }
        }
      }
    }

    void writeProperties(Object bean, PrintWriter out) {
      for (PropertyDescriptor pds : properties.values()) {
        Method getter = pds.getReadMethod();
        try {
          Object value = getter.invoke(bean, (Object[]) null);
          if (value == null) {
            continue;
          }
          if (value instanceof String) {
            value = XMLStore.quote((String) value);
          } else if (value instanceof Date) {
            value = ((Date) value).getTime();
          }
          out.print(pds.getName() + "='" + value + "' ");
          if (debugBean) {
            System.out.println(" property get " + pds.getName() + "='" + value + "' ");
          }
        } catch (InvocationTargetException | IllegalAccessException e) {
          e.printStackTrace();
        }
      }
      if (properties.values().isEmpty()) {
        out.print("value ='" + bean + "' ");
      }
    }

    void readProperties(Object bean, org.xml.sax.Attributes atts) {
      for (PropertyDescriptor pds : properties.values()) {
        Method setter = pds.getWriteMethod();
        if (setter == null) {
          continue;
        }

        try {
          String sArg = atts.getValue(pds.getName());
          Object arg = getArgument(pds.getPropertyType(), sArg);
          if (debugBean) {
            System.out.println(" property set " + pds.getName() + "=" + sArg + " == " + arg);
          }
          if (arg == null) {
            continue;
          }
          args[0] = arg;
          setter.invoke(bean, args);
        } catch (NumberFormatException | InvocationTargetException | IllegalAccessException e) {
          e.printStackTrace();
        }
      }
    }

    private Object getArgument(Class<?> c, String value) {
      if (c == String.class) {
        return value;
      } else if (c == int.class) {
        return Integer.valueOf(value);
      } else if (c == double.class) {
        return Double.valueOf(value);
      } else if (c == boolean.class) {
        if (value == null) {
          // Boolean.valueOf(null) actually returns "false", which we don't want.
          return null;
        } else {
          return Boolean.valueOf(value);
        }
      } else if (c == float.class) {
        return Float.valueOf(value);
      } else if (c == short.class) {
        return Short.valueOf(value);
      } else if (c == long.class) {
        return Long.valueOf(value);
      } else if (c == byte.class) {
        return Byte.valueOf(value);
      } else if (c == Date.class) {
        long time = Long.parseLong(value);
        return new Date(time);
      } else {
        return null;
      }
    }
  }
}
