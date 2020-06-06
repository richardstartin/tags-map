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
    TagsMap<Long> map = TagsMap.create(Object.class, "x1", "x2");
    for (int j = 0; j < 100; ++j) {
      AtomicLong counter = new AtomicLong();
      AtomicLong x1 = new AtomicLong();
      AtomicLong x2 = new AtomicLong();
      CountDownLatch latch = new CountDownLatch(2);
      Thread x1Writer = new Thread(() -> {
        map.put("x1", counter.get());
        long count = 0;
        latch.countDown();
        for (int i = 0; i < 1000000; ++i) {
          count = counter.get();
          map.put("x1", count);
        }
        x1.set(count);
      });

      Thread x2Writer = new Thread(() -> {
        map.put("x2", counter.get());
        long count = 0;
        latch.countDown();
        for (int i = 0; i < 1000000; ++i) {
          count = counter.get();
          map.put("x2", count);
        }
        x2.set(count);
      });


      Thread reader = new Thread(() -> {
        try {
          latch.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }
        for (int i = 0; i < 1000000; ++i) {
          long m1 = x1.get();
          long m2 = x2.get();
          Long v1 = map.get("x1");
          Long v2 = map.get("x2");
          assertTrue(v1 <= counter.get());
          assertTrue(v1 >= m1);
          assertTrue(v2 <= counter.get());
          assertTrue(v2 >= m2);
          counter.incrementAndGet();
        }
      });
      reader.start();
      x1Writer.start();
      x2Writer.start();
      x1Writer.join();
      assertEquals(x1.get(), map.get("x1"));
      x2Writer.join();
      assertEquals(x2.get(), map.get("x2"));
      reader.join();
      map.makeImmutable();
      assertEquals(x1.get(), map.getExclusive("x1"));
      assertEquals(x2.get(), map.getExclusive("x2"));
      System.out.println("x1=" + map.getExclusive("x1"));
      System.out.println("x2=" + map.getExclusive("x2"));
    }
  }

}