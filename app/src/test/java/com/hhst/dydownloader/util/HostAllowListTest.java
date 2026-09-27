package com.hhst.dydownloader.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class HostAllowListTest {
  private static final List<String> HOSTS = List.of("douyin.com", "tiktok.com");

  @Test
  public void acceptsRootAndSubdomainsCaseInsensitively() {
    assertTrue(HostAllowList.matches("https://douyin.com/", HOSTS));
    assertTrue(HostAllowList.matches(" https://VM.TIKTOK.COM/share ", HOSTS));
    assertTrue(HostAllowList.matches("https://www.douyin.com/", List.of("DOUYIN.COM")));
  }

  @Test
  public void rejectsLookalikesUserInfoAndHostNamesOutsideTheAuthority() {
    for (String url : List.of(
        "https://eviltiktok.com/", "https://tiktok.com.evil.example/",
        "https://tiktok.com@evil.example/", "https://evil.example/?next=https://tiktok.com/",
        "https://evil.example/tiktok.com", "https://%74iktok.com/")) {
      assertFalse(url, HostAllowList.matches(url, HOSTS));
    }
  }

  @Test
  public void rejectsMissingAndMalformedHosts() {
    assertFalse(HostAllowList.matches(null, HOSTS));
    assertFalse(HostAllowList.matches("", HOSTS));
    assertFalse(HostAllowList.matches("https://[invalid", HOSTS));
    assertFalse(HostAllowList.matches("file:///tiktok.com", HOSTS));
    assertFalse(HostAllowList.matches("https://tiktok.com/", List.of()));
  }
}
