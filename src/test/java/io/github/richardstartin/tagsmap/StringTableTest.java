package io.github.richardstartin.tagsmap;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class StringTableTest {

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

  public static Stream<Arguments> randomStringSets() {
    return IntStream.range(0, 1000)
            .mapToObj(i -> {
              int count = ThreadLocalRandom.current().nextInt(1, 64);
              String[] args = new String[count];
              for (int s = 0; s < count; ++s) {
                args[s] = create(ThreadLocalRandom.current().nextInt(1, 20));
              }
              return Arguments.of(new HashSet<>(Arrays.asList(args)));
            });
  }

  @ParameterizedTest
  @MethodSource("randomStringSets")
  public void smokeTest(Set<String> keys) {
    StringTable table = new StringTable(keys.toArray(new String[0]));
    assertEquals(table.size(), keys.size());
    BitSet used = new BitSet(table.size());
    for (String key : keys) {
      int code = table.code(key);
      assertTrue(code >= 0);
      assertTrue(code < keys.size());
      assertFalse(used.get(code));
      used.set(code);
    }
  }

}