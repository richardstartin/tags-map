package io.github.richardstartin.tagsmap;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * A dense concurrent hashmap which only supports up to 64 predefined keys
 * @param <T>
 */
public class TagsMap<T> implements ConcurrentMap<String, T> {

  static final Unsafe UNSAFE;
  static final int ARRAY_BASE_OFFSET;
  static final int ARRAY_ELEMENT_SHIFT;
  static final long MASK_OFFSET;

  static {
    try {
      Field f = Unsafe.class.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      UNSAFE = (Unsafe) f.get(null);
      ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(Object[].class);
      ARRAY_ELEMENT_SHIFT = Integer.numberOfTrailingZeros(UNSAFE.arrayIndexScale(Object[].class));
      MASK_OFFSET = UNSAFE.objectFieldOffset(TagsMap.class.getDeclaredField("mask"));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static <T> TagsMap<T> create(StringTable table) {
    return new TagsMap<>(table);
  }

  private final StringTable stringTable;
  private final Object[] values;
  private long mask;

  private TagsMap(StringTable stringTable) {
    this.stringTable = stringTable;
    this.values = new Object[stringTable.size()];
  }

  @Override
  public int size() {
    return Long.bitCount(getMaskVolatile());
  }

  @Override
  public boolean isEmpty() {
    return getMaskVolatile() == 0L;
  }

  @Override
  public boolean containsKey(Object key) {
    int index = indexFor((String) key);
    if (index >= 0 && stringTable.get(index).equals(key)) {
      return (getMaskVolatile() & (1L << index)) != 0;
    }
    return false;
  }

  @Override
  public boolean containsValue(Object value) {
    if (null != value) {
      long mask = getMaskVolatile();
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
    int index = indexFor((String) key);
    if (index >= 0 && stringTable.get(index).equals(key)) {
      return readValueAtIndex(index);
    }
    return null;
  }

  public T getExclusive(Object key) {
    int index = indexFor((String) key);
    if (index >= 0 && (mask & 1L << index) != 0
            && stringTable.get(index).equals(key)) {
      return getRaw(index);
    }
    return null;
  }

  public T getRaw(Object key) {
    return getRaw(indexFor((String) key));
  }

  @SuppressWarnings("unchecked")
  public T getRaw(int code) {
    return (T) values[code];
  }

  public void putRaw(int code, T value) {
    values[code] = value;
  }

  public void putRaw(String key, T value) {
    values[indexFor(key)] = value;
  }

  public void removeRaw(String key) {
    values[indexFor(key)] = null;
  }

  public void removeRaw(int code) {
    values[code] = null;
  }

  @Override
  public T put(String key, T value) {
    int index = stringTable.code(key);
    if (index >= 0 && stringTable.get(index).equals(key)) {
      return setValueAtIndex(index, value);
    }
    throw new IllegalStateException("unregistered: " + key);
  }

  @Override
  public T remove(Object key) {
    int index = stringTable.code((String) key);
    if (index >= 0 && stringTable.get(index).equals(key)) {
      return removeValueAtIndex(index);
    }
    return null;
  }

  @Override
  public void putAll(Map<? extends String, ? extends T> m) {
    throw new IllegalStateException();
  }

  @Override
  public void clear() {
    UNSAFE.putLongVolatile(this, MASK_OFFSET, 0L);
    Arrays.fill(values, null);
  }

  @Override
  public Set<String> keySet() {
    return stringTable.keySet();
  }

  @Override
  public Collection<T> values() {
    long mask = getMaskVolatile();
    List<T> values;
    do {
      values = new ArrayList<>(Long.bitCount(mask));
      while (mask != 0) {
        values.add(readValueAtIndex(Long.numberOfTrailingZeros(mask)));
        mask &= (mask - 1);
      }
    } while (mask != getMaskVolatile());
    return values;
  }

  @Override
  public Set<Entry<String, T>> entrySet() {
    throw new IllegalStateException();
  }

  @Override
  public T putIfAbsent(String key, T value) {
    int index = stringTable.code(key);
    if (index >= 0 && stringTable.get(index).equals(key)) {
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

  @SuppressWarnings("unchecked")
  private T readValueAtIndex(int index) {
    return (T) UNSAFE.getObjectVolatile(values, arrayIndex(index));
  }

  @SuppressWarnings("unchecked")
  private T readValueAtIndex(long index) {
    return (T) UNSAFE.getObjectVolatile(values, index);
  }

  private T setValueAtIndexIfUnset(int index, T value) {
    long arrayIndex = arrayIndex(index);
    if (UNSAFE.compareAndSwapObject(values, arrayIndex, null, value)) {
      casOr(1L << index);
      return null;
    }
    return readValueAtIndex(arrayIndex);
  }

  public int indexFor(String key) {
    return stringTable.code(key);
  }

  /**
   * After calling this, the map can be used as if it were thread-local,
   * from the thread which calls this method. Once everything is settled,
   * and you have a reference to the same string table and know which keys
   * you want to access, you can use this hash map as if it's an array.
   */
  public synchronized void makeImmutable() {
    long mask = getMaskVolatile();
    this.mask = mask;
    while (mask != 0L) { // wait for any pending updates
      int index = Long.numberOfTrailingZeros(mask);
      values[index] = readValueAtIndex(index);
      mask &= (mask - 1);
    }
  }

  private long getMaskVolatile() {
    return UNSAFE.getLongVolatile(this, MASK_OFFSET);
  }

  private T setValueAtIndex(int index, T value) {
    long arrayIndex = arrayIndex(index);
    T old = readValueAtIndex(arrayIndex);
    UNSAFE.putOrderedObject(values, arrayIndex, value);
    casOr(1L << index);
    return old;
  }

  @SuppressWarnings("unchecked")
  private T removeValueAtIndex(int index) {
    long arrayIndex = arrayIndex(index);
    casAnd(~(1L << index));
    return (T) UNSAFE.getAndSetObject(values, arrayIndex, null);
  }

  private void casOr(long bit) {
    long oldMask;
    long newMask;
    do {
      oldMask = getMaskVolatile();
      newMask = oldMask | bit;
    } while (oldMask != newMask
            && !UNSAFE.compareAndSwapLong(this, MASK_OFFSET, oldMask, newMask));
  }

  private void casAnd(long bit) {
    long oldMask;
    long newMask;
    do {
      oldMask = getMaskVolatile();
      newMask = oldMask & bit;
    } while (oldMask != newMask
            && !UNSAFE.compareAndSwapLong(this, MASK_OFFSET, oldMask, newMask));
  }

  private long arrayIndex(int index) {
    return ARRAY_BASE_OFFSET + ((long)index << ARRAY_ELEMENT_SHIFT);
  }

}
