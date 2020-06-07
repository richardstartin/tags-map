package io.github.richardstartin.tagsmap;

import java.util.*;

import static io.github.richardstartin.tagsmap.TagsMap.INT_ARRAY_BASE_OFFSET;
import static io.github.richardstartin.tagsmap.TagsMap.UNSAFE;
import static java.nio.charset.StandardCharsets.UTF_8;

public class StringTable {

  private final String[] strings;
  private final byte[][] utf8;
  private final Set<String> keySet;
  private final int size;
  private final int[] values;
  private final int[] seeds;

  @SuppressWarnings("unchecked")
  StringTable(String... strings) {
    int length = 1 << -Integer.numberOfLeadingZeros(strings.length - 1);
    Set<String> keySet = new TreeSet<>();
    this.values = new int[length];
    Arrays.fill(values, -1);
    this.seeds = new int[length];
    this.strings = new String[strings.length];
    this.utf8 = new byte[strings.length][];
    for (int i = 0; i < strings.length; ++i) {
      this.strings[i] = strings[i];
      this.utf8[i] = strings[i].getBytes(UTF_8);
    }
    List<Bucket>[] buckets = new List[length];
    Arrays.setAll(buckets, i -> new ArrayList<>());
    for (int i = 0; i < strings.length; ++i) {
      String string = strings[i];
      int hash = string.hashCode();
      int modHash = hash & (length - 1);
      buckets[modHash].add(new Bucket(i, hash));
      keySet.add(string);
    }
    Arrays.sort(buckets, Comparator.comparingInt(l -> -l.size()));
    BitSet free = new BitSet(values.length);
    free.set(0, values.length);
    int[] entries = new int[length];
    Arrays.fill(entries, -1);
    int b = 0;
    for (; b < buckets.length && buckets[b].size() > 1; b++) {
      List<Bucket> subKeys = buckets[b];
      int seed = 0;
      nextSeed: while (true) {
        seed++;
        boolean marked = false;
        for (Bucket bucket : subKeys) {
          int i = xorShift(bucket.hash + seed) & (length - 1);
          if (entries[i] == -1 && values[i] == -1) {
            marked = true;
            entries[i] = bucket.position;
            continue;
          }
          if (marked) {
            Arrays.fill(entries, -1);
          }
          continue nextSeed;
        }
        break;
      }
      for (int e = 0; e < entries.length; ++e) {
        if (entries[e] != -1) {
          values[e] = entries[e];
          free.clear(e);
        }
      }
      seeds[subKeys.get(0).hash & (length - 1)] = seed;
    }

    int slot = free.nextSetBit(0);
    while (b < buckets.length && !buckets[b].isEmpty()) {
      Bucket bucket = buckets[b].get(0);
      values[slot] = bucket.position;
      seeds[bucket.hash & (length - 1)] = -slot - 1;
      ++b;
      free.set(slot, false);
      slot = free.nextSetBit(slot);
    }
    this.keySet = Collections.unmodifiableSet(keySet);
    this.size = strings.length;
  }


  public int code(String value) {
    int hash = value.hashCode();
    int seed = UNSAFE.getInt(seeds, arrayIndex(hash & (values.length - 1)));
    int index = seed < 0 ? -seed-1 : xorShift(seed + hash) & (values.length - 1);
    return UNSAFE.getInt(values, arrayIndex(index));
  }

  public int size() {
    return size;
  }

  public Set<String> keySet() {
    return keySet;
  }

  public String get(int code) {
    return strings[code];
  }

  public byte[] getEncoded(int code) {
    return utf8[code];
  }

  public byte[] getEncoded(String value) {
    int code = code(value);
    return code >= 0 && value.equals(strings[code]) ? utf8[code] : null;
  }

  private static class Bucket {
    int position;
    int hash;

    public Bucket(int position, int hash) {
      this.position = position;
      this.hash = hash;
    }
  }

  private static int xorShift(int x) {
    x ^= x << 13;
    x ^= x >>> 17;
    x ^= x << 5;
    return x;
  }

  private long arrayIndex(int index) {
    return INT_ARRAY_BASE_OFFSET + (index << 2);
  }
}
