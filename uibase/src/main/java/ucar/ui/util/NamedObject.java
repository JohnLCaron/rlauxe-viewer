/*
 * Copyright (c) 1998-2018 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.ui.util;

/** An object that has a name and a description. */
public interface NamedObject {

  /** Get the object's name */
  String getName();

  /** Get the object's description. */
  String getDescription();

  /** Get the object itself */
  Object getValue();

  static NamedObject create(String name, String desc, Object value) {
    return Value.create(name, desc, value);
  }

  static NamedObject create(Object value, String desc) {
    return Value.create(value.toString(), desc, value);
  }

 public record Value(String name, String description, Object value) implements NamedObject {
   @Override
   public String getName() {
     return name;
   }

   @Override
   public String getDescription() {
     return description;
   }

   @Override
   public Object getValue() {
     return value;
   }

   private static NamedObject create(String name, String desc, Object value) {
      return new Value(name, desc, value);
    }
  }

}
