package com.hhst.dydownloader.tiktok;

import com.hhst.dydownloader.util.CustomBase64;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * TikTok Web 请求签名，移植自 DouK-Downloader (TikTokDownloader) 的 webmssdk
 * (2.0.0.561) 逆向实现，与 src/encrypt/tiktok_sign.py 保持一致。
 *
 * <p>签名参数及其顺序由 SDK 决定：
 * {@code <业务 query>&X-Dynosaur=..&msToken=..&X-Bogus=1&X-Gnarly=..}。
 * X-Gnarly 封印的 query 包含 X-Dynosaur 与 msToken，签发后调序即失效。
 */
public final class TikTokRequestSigner {
  private static final long MASK32 = 0xFFFFFFFFL;

  // 签名参数，顺序由 SDK 决定
  private static final String DYNOSAUR_PARAM = "X-Dynosaur";
  private static final String MS_TOKEN_PARAM = "msToken";
  private static final String BOGUS_PARAM = "X-Bogus";
  private static final String GNARLY_PARAM = "X-Gnarly";

  // HTTP 请求上 X-Bogus 为字面量 "1"；16 位 X-Bogus 只出现在 websocket 握手
  private static final String BOGUS_VALUE = "1";

  // 自定义 base64 字母表（相对标准表的位置置换），SDK 字符串表原文
  private static final String ALPHABET = "u09tbS3UvgDEe6r-ZVMXzLpsAohTn7mdINQlW412GqBjfYiyk8JORCF5/xKHwacP";

  // 信封首字节
  private static final int ENVELOPE_TAG = 0x4B;

  // ChaCha 状态字 0..3，非教科书 "expand 32-byte k"，来自 SDK 原文
  private static final int[] CHACHA_INIT = {(int) 1196819126L, 600974999, (int) 3863347763L, 1451689750};

  // FNV-1a 32 位变体：非标准偏移基数，每字节额外乘 33
  private static final long FNV_OFFSET = 2166136260L;
  private static final long FNV_PRIME = 16777619L;

  // 载荷携带的 SDK 与 bundle 版本。
  private static final String SDK_VERSION = "5.3.2";
  private static final String SCM_VERSION = "2.0.0.561";

  // 环境指纹；账号列表接口对这些值敏感。
  private static final int ENV_CODE = 65;
  private static final int UB_CODE = 8;

  // X-Dynosaur 字段 0x38，跨 bundle 与环境扰动恒定，是 SDK 内部 md5 的 hash_state
  private static final long VM_STATE_HASH = 0xC46CE353L;

  // Canvas 指纹：-1 表示"无 canvas"，无 2d 上下文的页面即上报此值
  private static final String CANVAS_HASH = "-1";

  // Node 桩环境无法产生、按 "0" 钉住的三个环境字段
  private static final String WEBGL_HASH = "0";
  private static final String COMPONENT_VERSION = "0";
  private static final String DEVICE_HASH = "0";

  // SDK 认为自身所在页面的 location.host + location.pathname
  private static final String PAGE = "www.tiktok.com/";

  // SDK 从 1 开始计自己的签名次数并写入四个字段，新页面首次签名为 1
  static final int CALL_SEQUENCE_START = 1;

  // 两个字节数组编码器 (xor_base, add_base, pre_xor, rot, post_add)：
  // A 编码除校验和外的所有字段；B 编码校验和与三个标志位
  private static final int[] ENCODER_A = {103, 1, -1, 2, 1};
  private static final int[] ENCODER_B = {102, 0, 165, 1, 0};

  // 每个信封内嵌的密钥字数
  private static final int KEY_WORDS = 12;

  private TikTokRequestSigner() {}

  /**
   * 对业务参数计算完整签名，返回可直接拼接在接口地址后的完整 query 字符串。
   *
   * @param params 业务参数（不含签名参数；msToken 若包含在内会被摘出并移至
   *     SDK 固定位置，不会出现第二份）
   * @param userAgent 请求使用的 User-Agent，必须与实际发送的一致
   * @param msToken 会话令牌，按原样封入（含空值），绝不伪造
   */
  public static String sign(Map<String, String> params, String userAgent, String msToken) {
    return sign(
        params, userAgent, msToken, System.currentTimeMillis() / 1000L, clockNonce(),
        ~clockNonce() & MASK32, null, null, new Random());
  }

  /** 全参数版本，供测试注入固定时钟、随机数与密钥。 */
  public static String sign(
      Map<String, String> params,
      String userAgent,
      String msToken,
      long timestampSeconds,
      long nonce,
      long nonce2,
      long[] key1,
      long[] key2,
      Random rng) {
    List<String[]> pairs = new ArrayList<>();
    if (params != null) {
      for (Map.Entry<String, String> entry : params.entrySet()) {
        pairs.add(new String[] {entry.getKey(), entry.getValue() == null ? "" : entry.getValue()});
      }
    }
    String query = encodeQuery(pairs);
    long[] firstKey = key1 != null ? key1 : randomKey(rng);
    long[] secondKey = key2 != null ? key2 : randomKey(rng);
    return sign(
        query, userAgent, msToken, timestampSeconds, nonce, nonce2,
        firstKey, secondKey, CALL_SEQUENCE_START);
  }

  /** 与上游 tiktok_sign.sign 一致：query 为最终发送的字节序（已按浏览器规则编码）。 */
  static String sign(
      String query,
      String userAgent,
      String msToken,
      long timestampSeconds,
      long firstNonce,
      long secondNonce,
      long[] key1,
      long[] key2,
      int sequence) {
    // 从 query 字符串摘出 msToken（仅拆分，不解码，字节序不变）
    List<String> parts = new ArrayList<>();
    String embeddedToken = "";
    for (String part : query.split("&")) {
      if (part.isEmpty()) {
        continue;
      }
      int separator = part.indexOf('=');
      String name = separator >= 0 ? part.substring(0, separator) : part;
      if (MS_TOKEN_PARAM.equals(name)) {
        embeddedToken = separator >= 0 ? part.substring(separator + 1) : "";
        continue;
      }
      parts.add(part);
    }
    // query 自带的 msToken 是权威值；否则使用调用方传入的 token
    String token = embeddedToken.isEmpty() ? (msToken == null ? "" : msToken) : embeddedToken;
    String businessQuery = String.join("&", parts);

    String dynosaur =
        seal(
            dynosaurPayload(
                businessQuery, userAgent, timestampSeconds, firstNonce, sequence),
            key1);
    // 封印覆盖追加了 X-Dynosaur 与 msToken 之后的 query，因此二者必须
    // 在 X-Gnarly 计算前拼接；夹在中间的 X-Bogus 不在封印范围内
    String sealedQuery =
        businessQuery + "&" + DYNOSAUR_PARAM + "=" + dynosaur + "&" + MS_TOKEN_PARAM + "=" + token;
    String gnarly =
        seal(
            gnarlyPayload(
                sealedQuery, userAgent, new byte[0], timestampSeconds, firstNonce, secondNonce,
                sequence),
            key2);
    // 原样拼接而非百分号编码：字母表含 "/"，填充为 "="，TikTok 页面对二者同样不做转义
    return sealedQuery + "&" + BOGUS_PARAM + "=" + BOGUS_VALUE + "&" + GNARLY_PARAM + "=" + gnarly;
  }

  // ------------------------------------------------------------------
  // TikTok 浏览器序列化规则
  // ------------------------------------------------------------------

  /** TikTok 的浏览器序列化不是 application/x-www-form-urlencoded：空格为 %20，括号、斜杠、冒号保持原样。 */
  private static String encodeQuery(List<String[]> pairs) {
    StringBuilder builder = new StringBuilder();
    for (String[] pair : pairs) {
      if (builder.length() > 0) {
        builder.append('&');
      }
      builder.append(escape(pair[0])).append('=').append(escape(pair[1]));
    }
    return builder.toString();
  }

  private static String escape(String text) {
    StringBuilder builder = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char current = text.charAt(i);
      if (current == ' ') {
        builder.append("%20");
      } else if (current == '"') {
        builder.append("%22");
      } else if (current == '<') {
        builder.append("%3C");
      } else if (current == '>') {
        builder.append("%3E");
      } else if (current == '`') {
        builder.append("%60");
      } else if (current == '#') {
        builder.append("%23");
      } else if (current > ' ' && current <= '~') {
        builder.append(current);
      } else {
        // 代理对按整体取 UTF-8 字节，避免低位代理被单独编码
        String unit = String.valueOf(current);
        if (Character.isHighSurrogate(current)
            && i + 1 < text.length()
            && Character.isLowSurrogate(text.charAt(i + 1))) {
          unit = text.substring(i, i + 2);
          i++;
        }
        for (byte value : utf8(unit)) {
          appendPercentByte(builder, value & 0xFF);
        }
      }
    }
    return builder.toString();
  }

  private static void appendPercentByte(StringBuilder builder, int value) {
    builder.append('%');
    builder.append(Character.toUpperCase(Character.forDigit((value >> 4) & 0xF, 16)));
    builder.append(Character.toUpperCase(Character.forDigit(value & 0xF, 16)));
  }

  // ------------------------------------------------------------------
  // 载荷构造
  // ------------------------------------------------------------------

  /** SDK 的 FNV-1a 变体：常规轮之后乘 33。 */
  static long hashState(String text) {
    long value = FNV_OFFSET;
    for (byte current : utf8(text)) {
      long step = ((value ^ (current & 0xFF)) * FNV_PRIME) & MASK32;
      value = (step + ((step * 32) & MASK32)) & MASK32;
    }
    return value;
  }

  /** 单个载荷字段：按位置的字节扰乱，填充后带长度标签。 */
  private static byte[] encodeField(String text, int[] config) {
    int xorBase = config[0];
    int addBase = config[1];
    int preXor = config[2];
    int rotate = config[3];
    int postAdd = config[4];
    int size = Math.max(text.length() + 2, 6);
    byte[] out = new byte[size];
    for (int index = 0; index < text.length(); index++) {
      long value = ((long) text.charAt(index) ^ (xorBase + index)) & MASK32;
      value = (value + addBase + (170 & index)) % 256;
      if (preXor >= 0) {
        value ^= preXor;
      }
      value = ((value << rotate) | (value >> (8 - rotate))) & 0xFF;
      out[index] = (byte) ((value ^ 187) + postAdd);
    }
    for (int index = text.length(); index < size - 2; index++) {
      out[index] = (byte) (221 + index);
    }
    out[size - 2] = 0;
    out[size - 1] = (byte) text.length();
    return out;
  }

  /** 字段铺成平台解析的 TLV 条目（key、0x00、length、value...）。 */
  private static byte[] packPayload(Map<Integer, byte[]> fields, boolean leadCount) {
    List<Integer> keys = new ArrayList<>(fields.keySet());
    keys.sort(Integer::compareTo);
    int total = leadCount ? 1 : 0;
    for (Integer key : keys) {
      total += 3 + fields.get(key).length;
    }
    byte[] out = new byte[total];
    int offset = 0;
    if (leadCount) {
      out[offset++] = (byte) keys.size();
    }
    for (Integer key : keys) {
      byte[] value = fields.get(key);
      out[offset++] = key.byteValue();
      out[offset++] = 0;
      out[offset++] = (byte) value.length;
      System.arraycopy(value, 0, out, offset, value.length);
      offset += value.length;
    }
    return out;
  }

  private static byte[] be(long value, int size) {
    ByteBuffer buffer = ByteBuffer.allocate(size);
    for (int i = size - 1; i >= 0; i--) {
      buffer.put(i, (byte) ((value >> (8 * (size - 1 - i))) & 0xFF));
    }
    return buffer.array();
  }

  /** 把两个 32 位值折叠进 16 位，并在高位盖上环境标记。 */
  private static long mixState(long timestamp, long nonce, int envCode) {
    long folded = ((timestamp >> 16) ^ (nonce >> 16) ^ timestamp ^ nonce) & 0xFFFF;
    return folded | ((long) envCode << 16);
  }

  /** X-Gnarly 字段值的 XOR 折叠，种子为全 1。 */
  private static long foldChecksum(List<Object> values, int mode) {
    long accumulator = MASK32;
    for (Object value : values) {
      long number;
      if (value instanceof String) {
        byte[] bytes = utf8((String) value);
        number = 0;
        if (mode != 1) {
          int limit = Math.min(4, bytes.length);
          for (int i = 0; i < limit; i++) {
            number = (number << 8) | (bytes[i] & 0xFF);
          }
        }
      } else {
        number = (Long) value;
      }
      accumulator ^= number & MASK32;
    }
    return accumulator & MASK32;
  }

  // ------------------------------------------------------------------
  // ChaCha 变体
  // ------------------------------------------------------------------

  private static void quarterRound(int[] state, int a, int b, int c, int d) {
    state[a] += state[b];
    state[d] = Integer.rotateLeft(state[d] ^ state[a], 16);
    state[c] += state[d];
    state[b] = Integer.rotateLeft(state[b] ^ state[c], 12);
    state[a] += state[b];
    state[d] = Integer.rotateLeft(state[d] ^ state[a], 8);
    state[c] += state[d];
    state[b] = Integer.rotateLeft(state[b] ^ state[c], 7);
  }

  /**
   * 一个 64 字节块。这不是 ChaCha20，差异是关键。
   *
   * <p>rounds 计单轮次数且随数据变化（5..20），奇数轮在列轮后退出；
   * 对角轮第三四元组是 (2, 7, 12, 13)（12 出现两次），系原实现缺陷，刻意复现。
   */
  private static int[] keystream(int[] state, int rounds) {
    int[] working = state.clone();
    int done = 0;
    while (done < rounds) {
      quarterRound(working, 0, 4, 8, 12);
      quarterRound(working, 1, 5, 9, 13);
      quarterRound(working, 2, 6, 10, 14);
      quarterRound(working, 3, 7, 11, 15);
      done++;
      if (done >= rounds) {
        break;
      }
      quarterRound(working, 0, 5, 10, 15);
      quarterRound(working, 1, 6, 11, 12);
      quarterRound(working, 2, 7, 12, 13);
      quarterRound(working, 3, 4, 13, 14);
      done++;
    }
    int[] out = new int[16];
    for (int i = 0; i < 16; i++) {
      out[i] = working[i] + state[i];
    }
    return out;
  }

  /** 把载荷按小端 uint32 字读入，与密钥流异或。 */
  private static byte[] crypt(int[] key, int rounds, byte[] payload) {
    int[] state = new int[16];
    System.arraycopy(CHACHA_INIT, 0, state, 0, 4);
    System.arraycopy(key, 0, state, 4, 12);
    int wordCount = (payload.length + 3) / 4;
    int[] words = new int[wordCount];
    for (int i = 0; i < wordCount; i++) {
      int value = 0;
      for (int j = 0; j < 4; j++) {
        int index = 4 * i + j;
        if (index < payload.length) {
          value |= (payload[index] & 0xFF) << (8 * j);
        }
      }
      words[i] = value;
    }
    int offset = 0;
    while (offset + 16 < wordCount) {
      int[] block = keystream(state, rounds);
      state[12] = state[12] + 1;
      for (int i = 0; i < 16; i++) {
        words[offset + i] ^= block[i];
      }
      offset += 16;
    }
    int[] tailBlock = keystream(state, rounds);
    for (int i = 0; i < wordCount - offset; i++) {
      words[offset + i] ^= tailBlock[i];
    }
    byte[] out = new byte[payload.length];
    for (int i = 0; i < wordCount; i++) {
      for (int j = 0; j < 4 && 4 * i + j < payload.length; j++) {
        out[4 * i + j] = (byte) ((words[i] >> (8 * j)) & 0xFF);
      }
    }
    return out;
  }

  /** 加密、把密钥拼回密文、编码。拼接位置由字节和决定，服务端据此回收密钥。 */
  static String seal(byte[] payload, long[] keyWords) {
    int[] key = new int[keyWords.length];
    int keyLowSum = 0;
    for (int i = 0; i < keyWords.length; i++) {
      key[i] = (int) keyWords[i];
      keyLowSum += key[i] & 0xF;
    }
    int rounds = (keyLowSum & 0xF) + 5;
    byte[] ciphertext = crypt(key, rounds, payload);
    byte[] keyBytes = new byte[keyWords.length * 4];
    long keySum = 0;
    for (int i = 0; i < keyWords.length; i++) {
      byte[] wordBytes = be(keyWords[i] & MASK32, 4);
      // 小端字节序
      keyBytes[4 * i] = wordBytes[3];
      keyBytes[4 * i + 1] = wordBytes[2];
      keyBytes[4 * i + 2] = wordBytes[1];
      keyBytes[4 * i + 3] = wordBytes[0];
      for (byte value : wordBytes) {
        keySum += value & 0xFF;
      }
    }
    long cipherSum = 0;
    for (byte value : ciphertext) {
      cipherSum += value & 0xFF;
    }
    int position = (int) ((keySum + cipherSum) % (ciphertext.length + 1));
    byte[] raw = new byte[1 + keyBytes.length + ciphertext.length];
    raw[0] = (byte) ENVELOPE_TAG;
    int offset = 1;
    System.arraycopy(ciphertext, 0, raw, offset, position);
    offset += position;
    System.arraycopy(keyBytes, 0, raw, offset, keyBytes.length);
    offset += keyBytes.length;
    System.arraycopy(ciphertext, position, raw, offset, ciphertext.length - position);
    return CustomBase64.encode(raw, ALPHABET);
  }

  private static long[] randomKey(Random rng) {
    long[] key = new long[KEY_WORDS];
    for (int i = 0; i < KEY_WORDS; i++) {
      key[i] = rng.nextInt() & MASK32;
    }
    return key;
  }

  /** 取自微秒时钟的 32 位值，与 SDK 的取法一致。 */
  private static long clockNonce() {
    return (System.currentTimeMillis() * 1000L) & MASK32;
  }

  // ------------------------------------------------------------------
  // 载荷
  // ------------------------------------------------------------------

  /**
   * 环境报告：25 个 TLV 字段，键 0x20..0x38 升序。
   *
   * <p>只有三个字段把报告与请求绑定（均为 hashState）：0x2B 对空字符串
   * （GET 无 body）、0x2E 对 query（仅 query，不含路径）、0x30 对 User-Agent。
   */
  private static byte[] dynosaurPayload(String query, String userAgent, long timestamp, long nonce, int sequence) {
    Map<Integer, byte[]> fields = new LinkedHashMap<>();
    long mixed = mixState(timestamp, nonce, ENV_CODE);
    fields.put(0x21, encodeField("1", ENCODER_B));
    fields.put(0x22, encodeField("1", ENCODER_B));
    fields.put(0x23, encodeField("0", ENCODER_A));
    fields.put(0x24, encodeField(String.valueOf(mixed), ENCODER_A));
    fields.put(0x25, encodeField(String.valueOf(sequence), ENCODER_A));
    fields.put(0x26, encodeField(String.valueOf(ENV_CODE), ENCODER_A));
    fields.put(0x27, encodeField(String.valueOf(timestamp), ENCODER_A));
    fields.put(0x28, encodeField(WEBGL_HASH, ENCODER_A));
    fields.put(0x29, encodeField("0", ENCODER_A));
    fields.put(0x2A, encodeField(SDK_VERSION, ENCODER_A));
    fields.put(0x2B, be(hashState(""), 4));
    fields.put(0x2C, encodeField(CANVAS_HASH, ENCODER_A));
    fields.put(0x2D, encodeField("0", ENCODER_A));
    fields.put(0x2E, be(hashState(query), 4));
    fields.put(0x2F, encodeField(String.valueOf(sequence), ENCODER_A));
    fields.put(0x30, be(hashState(userAgent), 4));
    fields.put(0x31, encodeField(SCM_VERSION, ENCODER_A));
    fields.put(0x32, encodeField(COMPONENT_VERSION, ENCODER_A));
    fields.put(0x33, encodeField(DEVICE_HASH, ENCODER_A));
    fields.put(0x34, encodeField(String.valueOf(nonce), ENCODER_A));
    fields.put(0x35, encodeField(PAGE, ENCODER_A));
    fields.put(0x36, encodeField(String.valueOf(UB_CODE), ENCODER_A));
    fields.put(0x37, encodeField("0", ENCODER_A));
    fields.put(0x38, be(VM_STATE_HASH, 4));
    // 占位：下方校验和会覆盖全部字段重新写入 0x20
    fields.put(0x20, encodeField("0", ENCODER_A));
    long checksum = 0;
    for (Map.Entry<Integer, byte[]> entry : fields.entrySet()) {
      checksum ^= entry.getValue()[1] & 0xFF;
    }
    fields.put(0x20, encodeField(String.valueOf(checksum), ENCODER_B));
    return packPayload(fields, false);
  }

  /** 请求封条的字段表，16 项（无 0x07）。 */
  private static byte[] gnarlyPayload(
      String signedQuery,
      String userAgent,
      byte[] body,
      long timestamp,
      long nonce,
      long nonce2,
      int sequence) {
    String queryMd5 = md5Hex(utf8(signedQuery));
    String bodyMd5 = md5Hex(body);
    String agentMd5 = md5Hex(utf8(userAgent));
    long mixed = mixState(timestamp, nonce, ENV_CODE);
    List<Object> covered = new ArrayList<>();
    covered.add(0L);
    covered.add((long) ENV_CODE);
    covered.add((long) UB_CODE);
    covered.add(queryMd5);
    covered.add(bodyMd5);
    covered.add(agentMd5);
    covered.add(timestamp & MASK32);
    covered.add(0L);
    covered.add(nonce & MASK32);
    covered.add(SDK_VERSION);
    covered.add(SCM_VERSION);
    covered.add((long) CALL_SEQUENCE_START);
    covered.add((long) sequence);
    covered.add((long) sequence);
    covered.add(mixed);
    covered.add(nonce2 & MASK32);
    long first = foldChecksum(covered, 2);
    List<Object> coveredWithFirst = new ArrayList<>(covered);
    coveredWithFirst.add(first);
    long second = foldChecksum(coveredWithFirst, 1);

    Map<Integer, byte[]> fields = new LinkedHashMap<>();
    fields.put(0x00, be(second, 4));
    fields.put(0x01, be(ENV_CODE, 2));
    fields.put(0x02, be(UB_CODE, 2));
    fields.put(0x03, utf8(queryMd5));
    fields.put(0x04, utf8(bodyMd5));
    fields.put(0x05, utf8(agentMd5));
    fields.put(0x06, be(timestamp & MASK32, 4));
    fields.put(0x08, be(nonce & MASK32, 4));
    fields.put(0x09, utf8(SDK_VERSION));
    fields.put(0x0A, utf8(SCM_VERSION));
    fields.put(0x0B, be(CALL_SEQUENCE_START, 2));
    fields.put(0x0C, be(sequence, 2));
    fields.put(0x0D, be(sequence, 2));
    fields.put(0x0E, be(mixed, 4));
    fields.put(0x0F, be(nonce2 & MASK32, 4));
    fields.put(0x10, be(first, 4));
    return packPayload(fields, true);
  }

  private static byte[] utf8(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static String md5Hex(byte[] input) {
    try {
      byte[] digest = MessageDigest.getInstance("MD5").digest(input);
      StringBuilder builder = new StringBuilder(digest.length * 2);
      for (byte value : digest) {
        builder.append(Character.forDigit((value >> 4) & 0xF, 16));
        builder.append(Character.forDigit(value & 0xF, 16));
      }
      return builder.toString();
    } catch (Exception e) {
      throw new IllegalStateException("MD5 unavailable", e);
    }
  }
}
