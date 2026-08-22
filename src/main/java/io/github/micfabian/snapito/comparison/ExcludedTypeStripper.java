package io.github.micfabian.snapito.comparison;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ExcludedTypeStripper {
  private final Set<Class<?>> excludedTypes;
  private final Map<Object, Object> visited = new IdentityHashMap<>();

  ExcludedTypeStripper(Set<Class<?>> excludedTypes) {
    this.excludedTypes = excludedTypes;
  }

  Object strip(Object value) {
    if (value == null || isExcluded(value)) {
      return null;
    }
    if (isScalar(value)) {
      return value;
    }
    Object cached = visited.get(value);
    if (cached != null) {
      return cached;
    }
    if (value instanceof Map<?, ?> map) {
      Map<Object, Object> result = new LinkedHashMap<>();
      visited.put(value, result);
      map.forEach((key, item) -> {
        if (!isExcluded(item)) {
          result.put(key, strip(item));
        }
      });
      return result;
    }
    if (value instanceof Collection<?> collection) {
      List<Object> result = new ArrayList<>(collection.size());
      visited.put(value, result);
      for (Object item : collection) {
        result.add(isExcluded(item) ? null : strip(item));
      }
      return result;
    }
    if (value.getClass().isArray()) {
      int size = Array.getLength(value);
      List<Object> result = new ArrayList<>(size);
      visited.put(value, result);
      for (int index = 0; index < size; index++) {
        Object item = Array.get(value, index);
        result.add(isExcluded(item) ? null : strip(item));
      }
      return result;
    }
    return stripBean(value);
  }

  private Object stripBean(Object value) {
    Map<String, Object> result = new LinkedHashMap<>();
    visited.put(value, result);
    boolean excludedAnything = false;
    for (PropertyDescriptor descriptor : propertyDescriptors(value.getClass())) {
      Method reader = descriptor.getReadMethod();
      if (reader == null || "class".equals(descriptor.getName())) {
        continue;
      }
      if (excludedTypes.stream().anyMatch(type -> type.isAssignableFrom(reader.getReturnType()))) {
        excludedAnything = true;
        continue;
      }
      try {
        reader.setAccessible(true);
        Object property = reader.invoke(value);
        if (isExcluded(property)) {
          excludedAnything = true;
          continue;
        }
        result.put(descriptor.getName(), strip(property));
      } catch (ReflectiveOperationException | RuntimeException ignored) {
        // an unreadable property contributes nothing to the snapshot
      }
    }
    if (result.isEmpty() && !excludedAnything) {
      return value;
    }
    return result;
  }

  private static List<PropertyDescriptor> propertyDescriptors(Class<?> type) {
    try {
      BeanInfo info = Introspector.getBeanInfo(type, Object.class);
      return List.of(info.getPropertyDescriptors());
    } catch (IntrospectionException e) {
      return List.of();
    }
  }

  private boolean isExcluded(Object value) {
    return value != null && excludedTypes.stream().anyMatch(type -> type.isInstance(value));
  }

  private static boolean isScalar(Object value) {
    return value instanceof CharSequence
      || value instanceof Number
      || value instanceof Boolean
      || value instanceof Character
      || value instanceof Enum<?>;
  }
}
