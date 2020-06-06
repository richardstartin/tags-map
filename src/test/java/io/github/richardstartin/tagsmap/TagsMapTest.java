package io.github.richardstartin.tagsmap;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class TagsMapTest {

  @Test
  public void getAndSet() {
    // associates a set of keys with the string class
    TagsMap<Object> map = TagsMap.create(String.class, "x1", "x2", "x3", "x4");
    assertNull(map.put("x1", "x1"));
    assertEquals("x1", map.get("x1"));
    assertNull(map.getExclusive("x1"));
    assertNull(map.get("x2"));
    assertNull(map.put("x2", 10));
    assertEquals(10, map.get("x2"));
    assertNull(map.getExclusive("x2"));
    map.makeImmutable();
    assertEquals("x1", map.getExclusive("x1"));
    assertEquals(10, map.getExclusive("x2"));
  }

  @Test
  public void visibilityTest() throws InterruptedException {
    AtomicLong counter = new AtomicLong();
    AtomicLong puts = new AtomicLong();
    TagsMap<Long> map = TagsMap.create(String.class, "x1", "x2");
    CountDownLatch latch = new CountDownLatch(1);
    Thread writer = new Thread(() -> {
      map.put("x1", counter.get());
      long count = 0;
      latch.countDown();
      for (int i = 0; i < 100000; ++i) {
        count = counter.get();
        map.put("x1", count);
      }
      puts.set(count);
    });


    Thread reader = new Thread(() -> {
      try {
        latch.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
      for (int i = 0; i < 100000; ++i) {
        Long value = map.get("x1");
        assertTrue(value <= counter.getAndIncrement());
        assertTrue(value >= puts.get());
      }
    });
    reader.start();
    writer.start();
    writer.join();
    assertEquals(puts.get(), map.get("x1"));
    reader.join();
    map.makeImmutable();
    assertEquals(puts.get(), map.getExclusive("x1"));
  }

}