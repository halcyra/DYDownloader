package com.hhst.dydownloader.db;

import androidx.room.TypeConverter;
import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.Platform;

public class Converters {
  @TypeConverter
  public static CardType fromString(String value) {
    return value == null ? null : CardType.valueOf(value);
  }

  @TypeConverter
  public static String fromCardType(CardType type) {
    return type == null ? null : type.name();
  }

  @TypeConverter
  public static Platform platformFromString(String value) {
    return value == null ? null : Platform.valueOf(value);
  }

  @TypeConverter
  public static String fromPlatform(Platform platform) {
    return platform == null ? null : platform.name();
  }
}
