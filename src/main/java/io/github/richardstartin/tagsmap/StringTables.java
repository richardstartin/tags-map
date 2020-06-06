package io.github.richardstartin.tagsmap;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class StringTables {

  private static final ConcurrentHashMap<Class<?>, String[]> KEYS = new ConcurrentHashMap<>();

  private static final ClassValue<StringTable> STRING_TABLES = new ClassValue<StringTable>() {
    @Override
    protected StringTable computeValue(Class<?> type) {
      return create(KEYS.get(type));
    }
  };

  public static void registerKeys(Class<?> klass, String... keys) {
    if (keys.length > 64) {
      throw new IllegalStateException();
    }
    String[] present = KEYS.putIfAbsent(klass, keys);
    if (null != present) {
      throw new IllegalStateException("keys " + Arrays.toString(present) + " already registered for class " + klass);
    }
  }

  public static StringTable create(Class<?> klass) {
    return STRING_TABLES.get(klass);
  }

  public static StringTable create(String... keys) {
    return new StringTable(keys);
  }
}
