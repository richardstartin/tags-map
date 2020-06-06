package io.github.richardstartin.tagsmap;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jol.info.ClassLayout;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

@State(Scope.Benchmark)
public class GetTagBenchmark {

  private static final byte[] LOWER_CASE = new byte[26];
  static {
    for (int i = 'a'; i <= 'z'; ++i) {
      LOWER_CASE[i - 'a'] = (byte)i;
    }
  }

  public static String create(int size) {
    byte[] bytes = new byte[size];
    for (int i = 0; i < size; ++i) {
      bytes[i] = LOWER_CASE[ThreadLocalRandom.current().nextInt(26)];
    }
    return new String(bytes, StandardCharsets.UTF_8);
  }

  @Param({"4", "8", "16"})
  int keyCount;

  ConcurrentMap<String, Object> chm;
  TagsMap<Object> tm;

  String[] keys;

  @Setup(Level.Trial)
  public void setup() {
    keys = new String[keyCount];
    for (int i = 0; i < keyCount; ++i) {
      keys[i] = create(10);
    }
    chm = new ConcurrentHashMap<>(keyCount);
    tm = TagsMap.create(Integer.class, keys);
    for (int i = 0; i < keyCount; ++i) {
      chm.put(keys[i], i);
      tm.put(keys[i], i);
    }
    System.out.println(ClassLayout.parseInstance(chm).toPrintable());
    System.out.println(ClassLayout.parseInstance(tm).toPrintable());
  }

  @Threads(1)
  @Benchmark
  public void tm1(Blackhole bh) {
    for (String key : keys) {
      bh.consume(tm.get(key));
    }
  }

  @Threads(2)
  @Benchmark
  public void tm2(Blackhole bh) {
    for (String key : keys) {
      bh.consume(tm.get(key));
    }
  }

  @Threads(4)
  @Benchmark
  public void tm4(Blackhole bh) {
    for (String key : keys) {
      bh.consume(tm.get(key));
    }
  }

  @Threads(1)
  @Benchmark
  public void chm1(Blackhole bh) {
    for (String key : keys) {
      bh.consume(chm.get(key));
    }
  }

  @Threads(2)
  @Benchmark
  public void chm2(Blackhole bh) {
    for (String key : keys) {
      bh.consume(chm.get(key));
    }
  }

  @Threads(4)
  @Benchmark
  public void chm4(Blackhole bh) {
    for (String key : keys) {
      bh.consume(chm.get(key));
    }
  }
}
