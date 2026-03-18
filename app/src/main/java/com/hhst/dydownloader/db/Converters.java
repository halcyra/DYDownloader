package com.hhst.dydownloader.db;

import androidx.room.TypeConverter;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.Platform;

public class Converters {
  @TypeConverter
  public static CardType fromString(String value) {
    if (value == null) {
      return null;
    }
    try {
      return CardType.valueOf(value);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  @TypeConverter
  public static String fromCardType(CardType type) {
    return type == null ? null : type.name();
  }

  @TypeConverter
  public static Platform platformFromString(String value) {
    if (value == null) {
      return null;
    }
    try {
      return Platform.valueOf(value);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  @TypeConverter
  public static String fromPlatform(Platform platform) {
    return platform == null ? null : platform.name();
  }
}
