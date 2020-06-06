package io.github.richardstartin.tagsmap;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A dense concurrent hashmap which only supports up to 64 predefined keys
 * @param <T>
 */
public class TagsMap<T> implements ConcurrentMap<String, T> {

  private static final Unsafe UNSAFE;
  private static final long MASK_OFFSET;

  static {
    try {
      Field f = Unsafe.class.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      UNSAFE = (Unsafe) f.get(null);
      MASK_OFFSET = UNSAFE.objectFieldOffset(TagsMap.class.getDeclaredField("mask"));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static final ConcurrentHashMap<Class<?>, String[]> KEYS = new ConcurrentHashMap<>();

  private static final ClassValue<StringTable> PERFECT_HASHES = new ClassValue<StringTable>() {
    @Override
    protected StringTable computeValue(Class<?> type) {
      return new StringTable(KEYS.get(type));
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

  public static <T> TagsMap<T> create(Class<?> type, String... keys) {
    registerKeys(type, keys);
    return create(type);
  }

  public static <T> TagsMap<T> create(Class<?> type) {
    return new TagsMap<>(type);
  }

  private final StringTable stringTable;
  private final Object[] values;
  private volatile long mask;

  private TagsMap(Class<?> klass) {
    this.stringTable = PERFECT_HASHES.get(klass);
    this.values = new Object[stringTable.size()];
  }

  @Override
  public int size() {
    return Long.bitCount(mask);
  }

  @Override
  public boolean isEmpty() {
    return mask == 0L;
  }

  @Override
  public boolean containsKey(Object key) {
    if (key instanceof String) {
      int index = stringTable.code((String) key);
      if (index >= 0) {
        long mask = this.mask;
        return (mask & (1L << index)) != 0;
      }
    }
    return false;
  }

  @Override
  public boolean containsValue(Object value) {
    // holy fuck why would anyone use this?
    if (null != value) {
      long mask = this.mask;
      while (mask != 0) {
        int pos = Long.numberOfTrailingZeros(mask);
        if (readValueAtIndex(pos).equals(value)) {
          return true;
        }
        mask &= (mask - 1);
      }
    }
    return false;
  }

  @Override
  public T get(Object key) {
    if (key instanceof String) {
      int index = stringTable.code((String) key);
      if (index >= 0) {
        long mask = this.mask;
        if ((mask & (1L << index)) != 0) {
          return readValueAtIndex(index);
        }
      }
    }
    return null;
  }

  @Override
  public T put(String key, T value) {
    int index = stringTable.code(key);
    if (index >= 0) {
      return setValueAtIndex(index, value);
    }
    throw new IllegalStateException("unregistered: " + key);
  }

  @Override
  public T remove(Object key) {
    if (key instanceof String) {
      int index = stringTable.code((String) key);
      long mask = this.mask;
      if (index >= 0 && (mask & 1L << index) != 0) {
        return removeValueAtIndex(index);
      }
    }
    return null;
  }

  @Override
  public void putAll(Map<? extends String, ? extends T> m) {
    throw new IllegalStateException();
  }

  @Override
  public void clear() {
    this.mask = 0L;
    Arrays.fill(values, null);
  }

  @Override
  public Set<String> keySet() {
    return stringTable.keySet();
  }

  @Override
  @SuppressWarnings("unchecked")
  public Collection<T> values() {
    long mask = this.mask;
    List<T> values = new ArrayList<>(Long.bitCount(mask));
    while (mask != 0) {
      values.add((T)this.values[Long.numberOfTrailingZeros(mask)]);
      mask &= (mask - 1);
    }
    return values;
  }

  @Override
  public Set<Entry<String, T>> entrySet() {
    throw new IllegalStateException();
  }

  @Override
  public T putIfAbsent(String key, T value) {
    int index = stringTable.code(key);
    if (index >= 0) {
      return setValueAtIndexIfUnset(index, value);
    }
    return null;
  }

  @Override
  public boolean remove(Object key, Object value) {
    throw new IllegalStateException();
  }

  @Override
  public boolean replace(String key, T oldValue, T newValue) {
    throw new IllegalStateException();
  }

  @Override
  public T replace(String key, T value) {
    throw new IllegalStateException();
  }

  // TODO array accesses are not synchronised at all
  @SuppressWarnings("unchecked")
  private T readValueAtIndex(int index) {
    // TODO needs concurrency control
    return (T) values[index];
  }

  private T setValueAtIndexIfUnset(int index, T value) {
    long oldMask;
    long newMask;
    do {
      oldMask = mask;
      newMask  = oldMask | (1L << index);
      if ((oldMask & (1L << index)) != 0) {
        return readValueAtIndex(index);
      }
    } while (!UNSAFE.compareAndSwapLong(this, MASK_OFFSET, oldMask, newMask));
    values[index] = value;
    return null;
  }

  private T setValueAtIndex(int index, T value) {
    T old = null;
    long oldMask;
    long newMask;
    do {
      oldMask = mask;
      if ((oldMask & (1L << index)) != 0) {
        old = readValueAtIndex(index);
      }
      newMask  = oldMask | (1L << index);
    } while (!UNSAFE.compareAndSwapLong(this, MASK_OFFSET, oldMask, newMask));
    values[index] = value;
    return old;
  }

  private T removeValueAtIndex(int index) {
    T value;
    long oldMask;
    long newMask;
    do {
      oldMask = mask;
      if ((oldMask & (1L << index)) != 0) {
        value = readValueAtIndex(index);
      } else {
        value = null;
      }
      newMask = oldMask & ~(1L << index);
    } while (!UNSAFE.compareAndSwapLong(this, MASK_OFFSET, oldMask, newMask));
    return value;
  }

}
