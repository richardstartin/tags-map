package io.github.richardstartin.tagsmap;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.ConcurrentHashMap;

@State(Scope.Benchmark)
public class PutTagBenchmark {

    @Param({"4", "8", "16", "32", "64"})
    int keyCount;

    ConcurrentHashMap<String, Object> chm;
    TagsMap<Object> tm;

    String[] keys;

    @Setup(Level.Trial)
    public void setup() {
        keys = new String[keyCount];
        for (int i = 0; i < keyCount; ++i) {
            keys[i] = Strings.create(10);
        }
        chm = new ConcurrentHashMap<>(keyCount);
        tm = TagsMap.create(StringTables.create(keys));
    }


    @Threads(1)
    @Benchmark
    public void tm1(Blackhole bh) {
        for (String key : keys) {
            bh.consume(tm.put(key, 1));
        }
    }

    @Threads(2)
    @Benchmark
    public void tm2(Blackhole bh) {
        for (String key : keys) {
            bh.consume(tm.put(key, 1));
        }
    }

    @Threads(4)
    @Benchmark
    public void tm4(Blackhole bh) {
        for (String key : keys) {
            bh.consume(tm.put(key, 1));
        }
    }

    @Threads(1)
    @Benchmark
    public void chm1(Blackhole bh) {
        for (String key : keys) {
            bh.consume(chm.put(key, 1));
        }
    }

    @Threads(2)
    @Benchmark
    public void chm2(Blackhole bh) {
        for (String key : keys) {
            bh.consume(chm.put(key, 1));
        }
    }

    @Threads(4)
    @Benchmark
    public void chm4(Blackhole bh) {
        for (String key : keys) {
            bh.consume(chm.put(key, 1));
        }
    }
}
