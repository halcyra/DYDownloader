package com.hhst.dydownloader.tiktok;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.LongSupplier;

public final class TikTokRequestSigner {
  private static final char[] STANDARD_BASE64_ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
  private final LongSupplier epochSecondsSupplier;

  public TikTokRequestSigner() {
    this(() -> System.currentTimeMillis() / 1000L);
  }

  public TikTokRequestSigner(LongSupplier epochSecondsSupplier) {
    this.epochSecondsSupplier =
        epochSecondsSupplier != null
            ? epochSecondsSupplier
            : () -> System.currentTimeMillis() / 1000L;
  }

  static int nextUnsignedInt(Random random) {
    if (random == null) {
      return 0;
    }
    // Android API 24 lacks Random.nextLong(bound); nextInt() already gives a uniform 32-bit value.
    return random.nextInt();
  }

  static String encodeBase64(byte[] input) {
    if (input == null || input.length == 0) {
      return "";
    }
    StringBuilder builder = new StringBuilder(((input.length + 2) / 3) * 4);
    for (int index = 0; index < input.length; index += 3) {
      int first = input[index] & 0xFF;
      int second = index + 1 < input.length ? input[index + 1] & 0xFF : 0;
      int third = index + 2 < input.length ? input[index + 2] & 0xFF : 0;
      int chunk = (first << 16) | (second << 8) | third;
      builder.append(STANDARD_BASE64_ALPHABET[(chunk >>> 18) & 0x3F]);
      builder.append(STANDARD_BASE64_ALPHABET[(chunk >>> 12) & 0x3F]);
      builder.append(
          index + 1 < input.length ? STANDARD_BASE64_ALPHABET[(chunk >>> 6) & 0x3F] : '=');
      builder.append(index + 2 < input.length ? STANDARD_BASE64_ALPHABET[chunk & 0x3F] : '=');
    }
    return builder.toString();
  }

  private static String encodeQuery(Map<String, String> params) {
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<String, String> entry : params.entrySet()) {
      if (builder.length() > 0) {
        builder.append('&');
      }
      builder.append(urlEncode(entry.getKey()));
      builder.append('=');
      builder.append(urlEncode(entry.getValue()));
    }
    return builder.toString();
  }

  private static String urlEncode(String value) {
    if (value == null) {
      return "";
    }
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte current : bytes) {
      int unsigned = current & 0xFF;
      if ((unsigned >= 'a' && unsigned <= 'z')
          || (unsigned >= 'A' && unsigned <= 'Z')
          || (unsigned >= '0' && unsigned <= '9')
          || unsigned == '-'
          || unsigned == '_'
          || unsigned == '.'
          || unsigned == '~') {
        builder.append((char) unsigned);
      } else {
        builder.append('%');
        builder.append(Character.toUpperCase(Character.forDigit((unsigned >> 4) & 0xF, 16)));
        builder.append(Character.toUpperCase(Character.forDigit(unsigned & 0xF, 16)));
      }
    }
    return builder.toString();
  }

  private static byte[] md5(byte[] input) {
    try {
      return MessageDigest.getInstance("MD5").digest(input);
    } catch (Exception e) {
      throw new IllegalStateException("MD5 unavailable", e);
    }
  }

  private static String md5Hex(byte[] input) {
    byte[] digest = md5(input);
    StringBuilder builder = new StringBuilder(digest.length * 2);
    for (byte value : digest) {
      builder.append(Character.forDigit((value >> 4) & 0xF, 16));
      builder.append(Character.forDigit(value & 0xF, 16));
    }
    return builder.toString();
  }

  public String sign(
      Map<String, String> params, String userAgent, String deviceId, String msToken) {
    long epochSeconds = Math.max(0L, epochSecondsSupplier.getAsLong());
    String safeUserAgent = userAgent == null || userAgent.isBlank() ? "Mozilla/5.0" : userAgent;

    Map<String, String> merged = new LinkedHashMap<>();
    if (params != null && !params.isEmpty()) {
      merged.putAll(new TreeMap<>(params));
    }
    if (deviceId != null && !deviceId.isBlank()) {
      merged.put("device_id", deviceId);
    }
    if (msToken != null && !msToken.isBlank()) {
      merged.put("msToken", msToken);
    }

    String query = encodeQuery(merged);
    String xBogus = new XBogus().getXBogus(query, 8, safeUserAgent, epochSeconds);
    String xGnarly =
        new XGnarly(epochSeconds * 1000L).generate(query, "", safeUserAgent, 0, "5.1.1");
    return query + "&X-Bogus=" + xBogus + "&X-Gnarly=" + xGnarly;
  }

  private static final class XBogus {
    private static final String ALPHABET =
        "Dkdpgh4ZKsQB80/Mfvw36XI1R25-WUAlEi7NLboqYTOPuzmFjJnryx9HVGcaStCe=";
    private static final int CANVAS = (int) 3873194319L;
    private static final int[] HEX_MAP = buildHexMap();

    private static int[] buildHexMap() {
      int[] array = new int[128];
      Arrays.fill(array, -1);
      for (char ch = '0'; ch <= '9'; ch++) {
        array[ch] = ch - '0';
      }
      for (char ch = 'a'; ch <= 'f'; ch++) {
        array[ch] = 10 + (ch - 'a');
      }
      return array;
    }

    private static int[] processUrlPath(String query) {
      String first = md5Hex(query.getBytes(StandardCharsets.UTF_8));
      byte[] firstBytes = hexToBytes(first);
      String second = md5Hex(firstBytes);
      return hexToUnsignedBytes(second);
    }

    private static byte[] hexToBytes(String hex) {
      byte[] bytes = new byte[hex.length() / 2];
      for (int index = 0; index < hex.length(); index += 2) {
        bytes[index / 2] =
            (byte) ((HEX_MAP[hex.charAt(index)] << 4) | HEX_MAP[hex.charAt(index + 1)]);
      }
      return bytes;
    }

    private static int[] hexToUnsignedBytes(String hex) {
      int[] bytes = new int[hex.length() / 2];
      for (int index = 0; index < hex.length(); index += 2) {
        bytes[index / 2] =
            ((HEX_MAP[hex.charAt(index)] << 4) | HEX_MAP[hex.charAt(index + 1)]) & 0xFF;
      }
      return bytes;
    }

    private static int[] disturbArray(int[] input) {
      return new int[] {
        input[0], input[2], input[4], input[6], input[8], input[10], input[12], input[14],
        input[16], input[18], input[1], input[3], input[5], input[7], input[9], input[11],
        input[13], input[15], input[17]
      };
    }

    private static String generateGarbledOne(int[] values) {
      int[] array = new int[19];
      array[0] = values[0];
      array[1] = values[5];
      array[2] = values[1];
      array[3] = values[6];
      array[4] = values[2];
      array[5] = values[7];
      array[6] = values[3];
      array[7] = values[8];
      array[8] = values[4];
      array[9] = values[9];
      array[10] = values[10];
      array[11] = values[15];
      array[12] = values[11];
      array[13] = values[16];
      array[14] = values[12];
      array[15] = values[17];
      array[16] = values[13];
      array[17] = values[18];
      array[18] = values[14];
      StringBuilder builder = new StringBuilder(array.length);
      for (int value : array) {
        builder.append((char) value);
      }
      return builder.toString();
    }

    private static String rc4(String key, String value) {
      int[] box = new int[256];
      for (int index = 0; index < 256; index++) {
        box[index] = index;
      }
      int j = 0;
      for (int index = 0; index < 256; index++) {
        j = (j + box[index] + key.charAt(index % key.length())) % 256;
        int temp = box[index];
        box[index] = box[j];
        box[j] = temp;
      }
      int i = 0;
      j = 0;
      StringBuilder builder = new StringBuilder(value.length());
      for (int index = 0; index < value.length(); index++) {
        i = (i + 1) % 256;
        j = (j + box[i]) % 256;
        int temp = box[i];
        box[i] = box[j];
        box[j] = temp;
        builder.append((char) (value.charAt(index) ^ box[(box[i] + box[j]) % 256]));
      }
      return builder.toString();
    }

    private static int[] generateNumbers(String text) {
      int[] numbers = new int[7];
      for (int index = 0; index < 21; index += 3) {
        numbers[index / 3] =
            (text.charAt(index) << 16) | (text.charAt(index + 1) << 8) | text.charAt(index + 2);
      }
      return numbers;
    }

    private static String generateChunk(int value) {
      char[] chars = new char[4];
      chars[0] = ALPHABET.charAt((value >> 18) & 63);
      chars[1] = ALPHABET.charAt((value >> 12) & 63);
      chars[2] = ALPHABET.charAt((value >> 6) & 63);
      chars[3] = ALPHABET.charAt(value & 63);
      return new String(chars);
    }

    private static int[] generateUaArray(String userAgent, int params) {
      byte[] key = new byte[] {0, 1, (byte) params};
      byte[] transformed = rc4Bytes(key, userAgent.getBytes(StandardCharsets.UTF_8));
      byte[] base64 = encodeBase64(transformed).getBytes(StandardCharsets.US_ASCII);
      byte[] digest = md5(base64);
      int[] result = new int[digest.length];
      for (int i = 0; i < digest.length; i++) {
        result[i] = digest[i] & 0xFF;
      }
      return result;
    }

    private static byte[] rc4Bytes(byte[] key, byte[] value) {
      int[] box = new int[256];
      for (int index = 0; index < 256; index++) {
        box[index] = index;
      }
      int j = 0;
      for (int index = 0; index < 256; index++) {
        j = (j + box[index] + (key[index % key.length] & 0xFF)) % 256;
        int temp = box[index];
        box[index] = box[j];
        box[j] = temp;
      }
      int i = 0;
      j = 0;
      byte[] output = new byte[value.length];
      for (int index = 0; index < value.length; index++) {
        i = (i + 1) % 256;
        j = (j + box[i]) % 256;
        int temp = box[i];
        box[i] = box[j];
        box[j] = temp;
        output[index] = (byte) (value[index] ^ box[(box[i] + box[j]) % 256]);
      }
      return output;
    }

    String getXBogus(String query, int params, String userAgent, long epochSeconds) {
      int timestamp = (int) epochSeconds;
      int[] queryDigest = processUrlPath(query);
      int[] uaArray = generateUaArray(userAgent, params);
      int[] array = {
        64,
        0,
        1,
        params,
        queryDigest[queryDigest.length - 2],
        queryDigest[queryDigest.length - 1],
        69,
        63,
        uaArray[uaArray.length - 2],
        uaArray[uaArray.length - 1],
        (timestamp >>> 24) & 0xFF,
        (timestamp >>> 16) & 0xFF,
        (timestamp >>> 8) & 0xFF,
        timestamp & 0xFF,
        (CANVAS >>> 24) & 0xFF,
        (CANVAS >>> 16) & 0xFF,
        (CANVAS >>> 8) & 0xFF,
        CANVAS & 0xFF,
        0
      };
      int checksum = 0;
      for (int i = 0; i < array.length - 1; i++) {
        checksum ^= array[i];
      }
      array[array.length - 1] = checksum & 0xFF;
      int[] disturbed = disturbArray(array);
      String garbled = generateGarbledOne(disturbed);
      String encrypted = rc4("\u00FF", garbled);
      String payload = new String(new char[] {2, (char) 255}) + encrypted;
      StringBuilder result = new StringBuilder();
      int[] numbers = generateNumbers(payload);
      for (int number : numbers) {
        result.append(generateChunk(number));
      }
      return result.toString();
    }
  }

  private static final class XGnarly {
    private static final long MASK32 = 0xFFFFFFFFL;
    private static final int[] OT = {(int) 1196819126L, (int) 600974999L, (int) 2903579748L, 45};
    private static final String BASE64_ALPHABET =
        "u09tbS3UvgDEe6r-ZVMXzLpsAohTn7mdINQlW412GqBjfYiyk8JORCF5/xKHwacP=";

    private final int[] state;
    private final Random random;
    private final long timestampMillis;
    private int position;

    XGnarly(long timestampMillis) {
      this.timestampMillis = Math.max(0L, timestampMillis);
      this.random = new Random(this.timestampMillis);
      this.position = 0;
      this.state =
          new int[] {
            (int) 2517678443L,
            53,
            (int) 3212677781L,
            (int) 2633865432L,
            (int) 217618912L,
            (int) 2931180889L,
            (int) 1498001188L,
            (int) 2157053261L,
            (int) 211147047L,
            32,
            (int) 2903579748L,
            (int) 3732962506L,
            (int) this.timestampMillis,
            nextRandomInt(),
            nextRandomInt(),
            nextRandomInt()
          };
    }

    private static int[] chachaBlock(int[] source, int rounds) {
      int[] work = source.clone();
      int round = 0;
      while (round < rounds) {
        quarter(work, 0, 4, 8, 12);
        quarter(work, 1, 5, 9, 13);
        quarter(work, 2, 6, 10, 14);
        quarter(work, 3, 7, 11, 15);
        round++;
        if (round >= rounds) {
          break;
        }
        quarter(work, 0, 5, 10, 15);
        quarter(work, 1, 6, 11, 12);
        quarter(work, 2, 7, 12, 13);
        quarter(work, 3, 4, 13, 14);
        round++;
      }
      for (int i = 0; i < work.length; i++) {
        work[i] =
            (int) ((Integer.toUnsignedLong(work[i]) + Integer.toUnsignedLong(source[i])) & MASK32);
      }
      return work;
    }

    private static void quarter(int[] state, int a, int b, int c, int d) {
      state[a] =
          (int) ((Integer.toUnsignedLong(state[a]) + Integer.toUnsignedLong(state[b])) & MASK32);
      state[d] = Integer.rotateLeft(state[d] ^ state[a], 16);
      state[c] =
          (int) ((Integer.toUnsignedLong(state[c]) + Integer.toUnsignedLong(state[d])) & MASK32);
      state[b] = Integer.rotateLeft(state[b] ^ state[c], 12);
      state[a] =
          (int) ((Integer.toUnsignedLong(state[a]) + Integer.toUnsignedLong(state[b])) & MASK32);
      state[d] = Integer.rotateLeft(state[d] ^ state[a], 8);
      state[c] =
          (int) ((Integer.toUnsignedLong(state[c]) + Integer.toUnsignedLong(state[d])) & MASK32);
      state[b] = Integer.rotateLeft(state[b] ^ state[c], 7);
    }

    private static int beIntFromString(String value) {
      byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
      int limit = Math.min(bytes.length, 4);
      int result = 0;
      for (int index = 0; index < limit; index++) {
        result = (result << 8) | (bytes[index] & 0xFF);
      }
      return result;
    }

    private static byte[] toNumberBytes(int value) {
      if (value < 65535) {
        return new byte[] {(byte) ((value >>> 8) & 0xFF), (byte) (value & 0xFF)};
      }
      return ByteBuffer.allocate(4).putInt(value).array();
    }

    private static void addAll(List<Integer> target, byte[] source) {
      for (byte value : source) {
        target.add(value & 0xFF);
      }
    }

    private static String toAsciiString(List<Integer> bytes) {
      StringBuilder builder = new StringBuilder(bytes.size());
      for (int value : bytes) {
        builder.append((char) value);
      }
      return builder.toString();
    }

    private static String encryptPayload(int[] keyWords, int rounds, String input) {
      int[] fullState = new int[16];
      System.arraycopy(OT, 0, fullState, 0, OT.length);
      System.arraycopy(keyWords, 0, fullState, OT.length, keyWords.length);
      byte[] data = input.getBytes(StandardCharsets.ISO_8859_1);
      encryptChaCha(fullState, rounds, data);
      return new String(data, StandardCharsets.ISO_8859_1);
    }

    private static void encryptChaCha(int[] keyWords, int rounds, byte[] data) {
      int wordCount = (data.length + 3) / 4;
      int[] words = new int[wordCount];
      int fullWords = data.length / 4;
      for (int i = 0; i < fullWords; i++) {
        int offset = i * 4;
        words[i] =
            (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
      }
      int leftover = data.length % 4;
      if (leftover > 0) {
        int offset = fullWords * 4;
        int value = 0;
        for (int i = 0; i < leftover; i++) {
          value |= (data[offset + i] & 0xFF) << (8 * i);
        }
        words[fullWords] = value;
      }

      int position = 0;
      int[] state = keyWords.clone();
      while (position + 16 < words.length) {
        int[] stream = chachaBlock(state, rounds);
        state[12] = (int) ((Integer.toUnsignedLong(state[12]) + 1) & MASK32);
        for (int i = 0; i < 16; i++) {
          words[position + i] ^= stream[i];
        }
        position += 16;
      }
      if (position < words.length) {
        int[] stream = chachaBlock(state, rounds);
        for (int i = 0; i < words.length - position; i++) {
          words[position + i] ^= stream[i];
        }
      }

      for (int i = 0; i < fullWords; i++) {
        int word = words[i];
        int offset = i * 4;
        data[offset] = (byte) (word & 0xFF);
        data[offset + 1] = (byte) ((word >>> 8) & 0xFF);
        data[offset + 2] = (byte) ((word >>> 16) & 0xFF);
        data[offset + 3] = (byte) ((word >>> 24) & 0xFF);
      }
      if (leftover > 0) {
        int word = words[fullWords];
        int offset = fullWords * 4;
        for (int i = 0; i < leftover; i++) {
          data[offset + i] = (byte) ((word >>> (8 * i)) & 0xFF);
        }
      }
    }

    private static String customBase64(String value) {
      StringBuilder builder = new StringBuilder((value.length() / 3) * 4);
      int fullLength = (value.length() / 3) * 3;
      for (int index = 0; index < fullLength; index += 3) {
        int block =
            (value.charAt(index) << 16) | (value.charAt(index + 1) << 8) | value.charAt(index + 2);
        builder.append(BASE64_ALPHABET.charAt((block >> 18) & 63));
        builder.append(BASE64_ALPHABET.charAt((block >> 12) & 63));
        builder.append(BASE64_ALPHABET.charAt((block >> 6) & 63));
        builder.append(BASE64_ALPHABET.charAt(block & 63));
      }
      return builder.toString();
    }

    String generate(
        String queryString, String body, String userAgent, int envcode, String version) {
      long seconds = timestampMillis / 1000L;
      Map<Integer, Object> values = new LinkedHashMap<>();
      values.put(1, 1);
      values.put(2, envcode);
      values.put(3, md5Hex(queryString.getBytes(StandardCharsets.UTF_8)));
      values.put(4, md5Hex(body.getBytes(StandardCharsets.UTF_8)));
      values.put(5, md5Hex(userAgent.getBytes(StandardCharsets.UTF_8)));
      values.put(6, (int) seconds);
      values.put(7, 1508145731);
      values.put(8, (int) ((timestampMillis * 1000L) % Integer.MAX_VALUE));
      values.put(9, version);

      if (!"5.1.1".equals(version) && !"5.1.0".equals(version)) {
        throw new IllegalArgumentException("Unsupported version: " + version);
      }
      if ("5.1.1".equals(version)) {
        values.put(10, "1.0.0.314");
        values.put(11, 1);
        int checksum = 0;
        for (int index = 1; index <= 11; index++) {
          Object value = values.get(index);
          checksum ^= value instanceof Integer ? (Integer) value : beIntFromString((String) value);
        }
        values.put(12, checksum);
      }

      int zero = 0;
      for (Object value : values.values()) {
        if (value instanceof Integer intValue) {
          zero ^= intValue;
        }
      }
      values.put(0, zero);

      List<Integer> payload = new ArrayList<>();
      payload.add(values.size());
      for (Map.Entry<Integer, Object> entry : values.entrySet()) {
        payload.add(entry.getKey());
        byte[] bytes =
            entry.getValue() instanceof Integer intValue
                ? toNumberBytes(intValue)
                : ((String) entry.getValue()).getBytes(StandardCharsets.UTF_8);
        addAll(payload, toNumberBytes(bytes.length));
        addAll(payload, bytes);
      }
      String base = toAsciiString(payload);

      int[] keyWords = new int[12];
      List<Integer> keyBytes = new ArrayList<>(48);
      int roundAccum = 0;
      for (int i = 0; i < keyWords.length; i++) {
        int word = (int) Math.floor(rand() * 4294967296.0d);
        keyWords[i] = word;
        roundAccum = (roundAccum + (word & 15)) & 15;
        keyBytes.add(word & 0xFF);
        keyBytes.add((word >>> 8) & 0xFF);
        keyBytes.add((word >>> 16) & 0xFF);
        keyBytes.add((word >>> 24) & 0xFF);
      }
      int rounds = roundAccum + 5;
      String encrypted = encryptPayload(keyWords, rounds, base);

      int insertPos = 0;
      for (int value : keyBytes) {
        insertPos = (insertPos + value) % (encrypted.length() + 1);
      }
      for (int index = 0; index < encrypted.length(); index++) {
        insertPos = (insertPos + encrypted.charAt(index)) % (encrypted.length() + 1);
      }

      StringBuilder finalBuilder = new StringBuilder();
      finalBuilder.append((char) (((1 << 6) ^ (1 << 3) ^ 3) & 0xFF));
      finalBuilder.append(encrypted, 0, insertPos);
      for (int value : keyBytes) {
        finalBuilder.append((char) value);
      }
      finalBuilder.append(encrypted.substring(insertPos));
      return customBase64(finalBuilder.toString());
    }

    private int nextRandomInt() {
      return nextUnsignedInt(random);
    }

    private double rand() {
      int[] block = chachaBlock(state, 8);
      long left = Integer.toUnsignedLong(block[position]);
      long right = (Integer.toUnsignedLong(block[position + 8]) & 0xFFFFFFF0L) >>> 11;
      if (position == 7) {
        state[12] = (int) ((Integer.toUnsignedLong(state[12]) + 1) & MASK32);
        position = 0;
      } else {
        position++;
      }
      return (left + 4294967296.0d * right) / Math.pow(2, 53);
    }
  }
}
