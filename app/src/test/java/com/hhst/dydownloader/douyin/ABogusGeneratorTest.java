package com.hhst.dydownloader.douyin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class ABogusGeneratorTest {
  private static final String USER_AGENT =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
          + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36";

  /**
   * 与上游 Python 实现逐字节比对的固定向量（DouK-Downloader src/encrypt/aBogus.py，
   * 固定 UA、Random(42)、now_ms，噪声字节完全一致）。
   */
  @Test
  public void getValue_matchesUpstreamReferenceVectors() {
    Mt19937Random rng = new Mt19937Random(42);
    ABogusGenerator generator = new ABogusGenerator(USER_AGENT);

    assertEquals(
        "QJ0RhtyjEoRcPVFG8Krf9aplJ99ANPSyBtT/bclPHPzbO1MbwRPcEaavGoKG4PA8zSBsiF3HqdMAbdncsUU0Zq"
            + "HkomkvSOhWBt5CVWXLhqw6GlG/LrmTe0hFuwBC0QvNe5ClEAD5WsMNIVxRVr5plBQaS5zqQbjgWNpcp2b9"
            + "tEWgfASki9-wOehpqg4z",
        generator.getValue("aid=6383&count=10&aweme_id=7345678901234567890", 1758213600000L, rng));
    assertEquals(
        "DvURDeS7Op/nOdKSmCaj9n3UQy6ArPSyQUT2WoPPeNFsO1UbLWPVEOe3boub4BA80SBkiKVHodMAbDdcTUX0ZK"
            + "npwmpvSBX6PT5CV6sLZqq6TUT/DNmTeggFqwBK0OkNe/9aEIDRIsMn2jdRIr5MldBGS5Fq5RfgWHB5pZby"
            + "SEW6fCSkh93hOHDdPyPe03KU",
        generator.getValue(
            "device_platform=webapp&aid=6383&channel=channel_pc_web&aweme_id=7345678901234567890"
                + "&msToken=abc",
            1758213601000L,
            rng));
    assertEquals(
        "Yj45DwtyDpmVFd/GYcrS9nolZ99/rs8yyGToWCBTCPP7O7MTNmPAENe3rouTssC8TSBiiKV7zdM/YEnc04UsZF"
            + "9pKmkkSTXSMT5AVW0LZqqgGlG/LqmhegDFowBCUQkNeA9HEIhRlsMrIfnR9N5ZlQQae5FoQcjgSNBApZt9"
            + "9EA6DA8kko3TO9gDT6TPUR9J",
        generator.getValue("", 1758213602000L, rng));
  }

  @Test
  public void getValue_usesS4AlphabetAndPadding() {
    ABogusGenerator generator = new ABogusGenerator(USER_AGENT);
    String value = generator.getValue("aid=6383&count=10");
    // s4 字母表不含 '+'；输出按 base64 对齐
    assertTrue(value.matches("[A-Za-z0-9/\\-=]+"));
    assertEquals(0, value.length() % 4);
  }

  @Test
  public void getValue_rejectsClockInSeconds() {
    ABogusGenerator generator = new ABogusGenerator(USER_AGENT);
    try {
      generator.getValue("aid=6383", 1758213600L, new Random());
      throw new AssertionError("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // 时钟早于纪元说明调用方传了秒而不是毫秒
    }
  }

  /**
   * Python random.Random 的 MT19937 复刻，用于让噪声字节与上游固定向量一致。
   * 仅实现测试所需的 random() 与 getrandbits(32)。
   */
  private static final class Mt19937Random extends Random {
    private static final int N = 624;
    private static final int M = 397;
    private static final long MATRIX_A = 0x9908B0DFL;
    private static final long UPPER_MASK = 0x80000000L;
    private static final long LOWER_MASK = 0x7FFFFFFFL;

    private final long[] mt = new long[N];
    private int mti = N + 1;

    Mt19937Random(long seed) {
      initByArray(new long[] {seed & 0xFFFFFFFFL});
    }

    private void initGenrand(long seed) {
      mt[0] = seed & 0xFFFFFFFFL;
      for (mti = 1; mti < N; mti++) {
        mt[mti] =
            (1812433253L * (mt[mti - 1] ^ (mt[mti - 1] >>> 30)) + mti) & 0xFFFFFFFFL;
      }
    }

    private void initByArray(long[] key) {
      initGenrand(19650218L);
      int i = 1;
      int j = 0;
      int k = Math.max(N, key.length);
      for (; k != 0; k--) {
        mt[i] =
            ((mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * 1664525L)) + key[j] + j) & 0xFFFFFFFFL;
        i++;
        j++;
        if (i >= N) {
          mt[0] = mt[N - 1];
          i = 1;
        }
        if (j >= key.length) {
          j = 0;
        }
      }
      for (k = N - 1; k != 0; k--) {
        mt[i] =
            ((mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 30)) * 1566083941L)) - i) & 0xFFFFFFFFL;
        i++;
        if (i >= N) {
          mt[0] = mt[N - 1];
          i = 1;
        }
      }
      mt[0] = 0x80000000L;
    }

    private long genrandInt32() {
      long y;
      if (mti >= N) {
        int kk;
        for (kk = 0; kk < N - M; kk++) {
          y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
          mt[kk] = mt[kk + M] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0);
        }
        for (; kk < N - 1; kk++) {
          y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
          mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0);
        }
        y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
        mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0);
        mti = 0;
      }
      y = mt[mti++];
      y ^= (y >>> 11);
      y ^= (y << 7) & 0x9D2C5680L;
      y ^= (y << 15) & 0xEFC60000L;
      y ^= (y >>> 18);
      return y & 0xFFFFFFFFL;
    }

    @Override
    public double nextDouble() {
      long a = genrandInt32() >>> 5;
      long b = genrandInt32() >>> 6;
      return (a * 67108864.0 + b) / 9007199254740992.0;
    }
  }
}
