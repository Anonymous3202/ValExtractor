package com.google.javascript.rhino.jstype;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;

class PropertyMap implements Serializable  {
  final private static long serialVersionUID = 1L;
  final private static PropertyMap EMPTY_MAP = new PropertyMap(ImmutableMap.<String, Property>of());
  final private static Function<ObjectType, PropertyMap> PROP_MAP_FROM_TYPE = new Function<ObjectType, PropertyMap>() {
      @Override() public PropertyMap apply(ObjectType t) {
        return t.getPropertyMap();
      }
  };
  private ObjectType parentSource = null;
  final private Map<String, Property> properties;
  PropertyMap() {
    this(Maps.<String, Property>newTreeMap());
  }
  private PropertyMap(Map<String, Property> underlyingMap) {
    super();
    this.properties = underlyingMap;
  }
  Iterable<Property> values() {
    return properties.values();
  }
  Iterable<PropertyMap> getSecondaryParents() {
    if(parentSource == null) {
      ImmutableList<PropertyMap> var_2698 = ImmutableList.of();
      return var_2698;
    }
    Iterable<ObjectType> extendedInterfaces = parentSource.getCtorExtendedInterfaces();
    if(Iterables.isEmpty(extendedInterfaces)) {
      return ImmutableList.of();
    }
    return Iterables.transform(extendedInterfaces, PROP_MAP_FROM_TYPE);
  }
  Property getOwnProperty(String propertyName) {
    return properties.get(propertyName);
  }
  Property getSlot(String name) {
    if(properties.containsKey(name)) {
      return properties.get(name);
    }
    PropertyMap primaryParent = getPrimaryParent();
    if(primaryParent != null) {
      Property prop = primaryParent.getSlot(name);
      if(prop != null) {
        return prop;
      }
    }
    for (PropertyMap p : getSecondaryParents()) {
      if(p != null) {
        Property prop = p.getSlot(name);
        if(prop != null) {
          return prop;
        }
      }
    }
    return null;
  }
  PropertyMap getPrimaryParent() {
    if(parentSource == null) {
      return null;
    }
    ObjectType iProto = parentSource.getImplicitPrototype();
    return iProto == null ? null : iProto.getPropertyMap();
  }
  static PropertyMap immutableEmptyMap() {
    return EMPTY_MAP;
  }
  Set<String> getOwnPropertyNames() {
    return properties.keySet();
  }
  boolean hasOwnProperty(String propertyName) {
    return properties.get(propertyName) != null;
  }
  boolean hasProperty(String propertyName) {
    return getSlot(propertyName) != null;
  }
  boolean removeProperty(String name) {
    return properties.remove(name) != null;
  }
  int getPropertiesCount() {
    PropertyMap primaryParent = getPrimaryParent();
    if(primaryParent == null) {
      return this.properties.size();
    }
    Set<String> props = Sets.newHashSet();
    collectPropertyNames(props);
    return props.size();
  }
  void collectPropertyNames(Set<String> props) {
    for (String prop : properties.keySet()) {
      props.add(prop);
    }
    PropertyMap primaryParent = getPrimaryParent();
    if(primaryParent != null) {
      primaryParent.collectPropertyNames(props);
    }
    for (PropertyMap p : getSecondaryParents()) {
      if(p != null) {
        p.collectPropertyNames(props);
      }
    }
  }
  void putProperty(String name, Property newProp) {
    Property oldProp = properties.get(name);
    if(oldProp != null) {
      newProp.setJSDocInfo(oldProp.getJSDocInfo());
    }
    properties.put(name, newProp);
  }
  void setParentSource(ObjectType ownerType) {
    if(this != EMPTY_MAP) {
      this.parentSource = ownerType;
    }
  }
}