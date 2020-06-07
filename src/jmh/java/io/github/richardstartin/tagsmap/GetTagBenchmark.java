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

  @Param({"4", "8", "16", "32", "64"})
  int keyCount;

  @Param({"true", "false"})
  boolean present;

  ConcurrentHashMap<String, Object> chm;
  TagsMap<Object> tm;

  String[] keys;
  int[] codes;

  @Setup(Level.Trial)
  public void setup() {
    keys = new String[keyCount];
    codes = new int[keyCount];
    for (int i = 0; i < keyCount; ++i) {
      keys[i] = Strings.create(10);
    }
    chm = new ConcurrentHashMap<>(keyCount);
    StringTable stringTable = StringTables.create(keys);
    tm = TagsMap.create(stringTable);
    for (int i = 0; i < keyCount; ++i) {
      chm.put(keys[i], i);
      tm.put(keys[i], i);
      codes[i] = stringTable.code(keys[i]);
    }
    if (!present) {
      for (int i = 0; i < keyCount; ++i) {
        keys[i] += 'A';
      }
    }
    tm.makeImmutable();
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
  public void tm1Exc(Blackhole bh) {
    for (String key : keys) {
      bh.consume(tm.getExclusive(key));
    }
  }

  @Threads(2)
  @Benchmark
  public void tm2Exc(Blackhole bh) {
    for (String key : keys) {
      bh.consume(tm.getExclusive(key));
    }
  }

  @Threads(4)
  @Benchmark
  public void tm4Exc(Blackhole bh) {
    for (String key : keys) {
      bh.consume(tm.getExclusive(key));
    }
  }

  @Threads(1)
  @Benchmark
  public void tm1Raw(Blackhole bh) {
    for (int code : codes) {
      bh.consume(tm.getRaw(code));
    }
  }

  @Threads(2)
  @Benchmark
  public void tm2Raw(Blackhole bh) {
    for (int code : codes) {
      bh.consume(tm.getRaw(code));
    }
  }

  @Threads(4)
  @Benchmark
  public void tm4Raw(Blackhole bh) {
    for (int code : codes) {
      bh.consume(tm.getRaw(code));
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
