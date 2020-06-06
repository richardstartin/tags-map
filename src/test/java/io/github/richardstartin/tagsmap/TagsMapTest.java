package io.github.richardstartin.tagsmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TagsMapTest {

  @Test
  public void getAndSet() {
    // associates a set of keys with the string class
    TagsMap<Object> map = TagsMap.create(String.class, "x1", "x2", "x3", "x4");
    assertNull(map.put("x1", "x1"));
    assertEquals("x1", map.get("x1"));
    assertNull(map.get("x2"));
    assertNull(map.put("x2", 10));
    assertEquals(10, map.get("x2"));
  }

}