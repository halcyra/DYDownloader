package com.hhst.dydownloader.db;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.Test;

public class PlatformColumnSchemaContractTest {

  private static final Pattern NON_NULL_DEFAULT_PLATFORM_FIELD =
      Pattern.compile(
          "(?s)(@NonNull\\s+@ColumnInfo\\(defaultValue = \"'DOUYIN'\"\\)|"
              + "@ColumnInfo\\(defaultValue = \"'DOUYIN'\"\\)\\s+@NonNull)\\s+"
              + "public Platform platform = Platform\\.DOUYIN;");
  private static final Pattern DATABASE_VERSION_7 =
      Pattern.compile("(?s)@Database\\([^)]*version\\s*=\\s*7");
  private static final Pattern MIGRATION_6_7 =
      Pattern.compile("new Migration\\(6,\\s*7\\)");

  @Test
  public void resourceEntity_platformColumnMatchesMigrationContract() throws IOException {
    assertTrue(
        NON_NULL_DEFAULT_PLATFORM_FIELD
            .matcher(readMainJava("com", "hhst", "dydownloader", "db", "ResourceEntity.java"))
            .find());
  }

  @Test
  public void downloadTaskEntity_platformColumnMatchesMigrationContract() throws IOException {
    assertTrue(
        NON_NULL_DEFAULT_PLATFORM_FIELD
            .matcher(
                readMainJava("com", "hhst", "dydownloader", "db", "DownloadTaskEntity.java"))
            .find());
  }

  @Test
  public void appDatabase_bumpsVersionAfterPlatformSchemaChange() throws IOException {
    assertTrue(
        DATABASE_VERSION_7
            .matcher(readMainJava("com", "hhst", "dydownloader", "db", "AppDatabase.java"))
            .find());
  }

  @Test
  public void appDatabase_declaresMigrationFrom6To7() throws IOException {
    assertTrue(
        MIGRATION_6_7
            .matcher(readMainJava("com", "hhst", "dydownloader", "db", "AppDatabase.java"))
            .find());
  }

  private static String readMainJava(String first, String... more) throws IOException {
    Path relativePath = Path.of("app", "src", "main", "java", first);
    for (String segment : more) {
      relativePath = relativePath.resolve(segment);
    }
    Path baseDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    for (int depth = 0; depth < 4 && baseDir != null; depth++) {
      Path candidate = baseDir.resolve(relativePath);
      if (Files.exists(candidate)) {
        return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
      }
      baseDir = baseDir.getParent();
    }
    throw new IOException("Could not locate source file: " + relativePath);
  }
}
