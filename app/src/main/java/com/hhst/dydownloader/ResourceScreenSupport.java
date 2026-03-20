package com.hhst.dydownloader;

import com.hhst.dydownloader.douyin.AwemeProfile;
import com.hhst.dydownloader.model.ResourceItem;
import com.hhst.dydownloader.share.ShareLinkResolver.LinkKind;
import com.hhst.dydownloader.util.StoragePathUtils;
import java.util.List;
import java.util.Objects;

final class ResourceScreenSupport {
  private ResourceScreenSupport() {}

  static String resolveDownloadGroupDir(
      LinkKind shareKind, List<AwemeProfile> allProfiles, String fallbackTitle) {
    if (shareKind == LinkKind.MIX) {
      return StoragePathUtils.joinSegments(resolveCollectionTitle(allProfiles, fallbackTitle));
    }
    if (shareKind == LinkKind.ACCOUNT) {
      return StoragePathUtils.joinSegments(resolveAccountTitle(allProfiles, fallbackTitle));
    }
    return StoragePathUtils.joinSegments(resolveWorkStorageTitle(allProfiles, fallbackTitle));
  }

  static boolean shouldPersistSnapshot(long resourceId, List<ResourceItem> resourceList) {
    return resourceId <= 0 && resourceList != null && !resourceList.isEmpty();
  }

  static String resolveCollectionTitle(List<AwemeProfile> allProfiles, String fallbackTitle) {
    if (allProfiles != null) {
      for (AwemeProfile profile : allProfiles) {
        if (profile != null
            && profile.collectionTitle() != null
            && !profile.collectionTitle().isBlank()) {
          return profile.collectionTitle().trim();
        }
      }
    }
    return fallbackTitle == null ? "" : fallbackTitle.trim();
  }

  static String resolveAccountTitle(List<AwemeProfile> allProfiles, String fallbackTitle) {
    String commonNickname = resolveCommonAuthorNickname(allProfiles);
    if (!commonNickname.isBlank()) {
      return commonNickname;
    }
    return fallbackTitle == null ? "" : fallbackTitle.trim();
  }

  private static String resolveCommonAuthorNickname(List<AwemeProfile> allProfiles) {
    if (allProfiles == null || allProfiles.isEmpty()) {
      return "";
    }
    String commonNickname = allProfiles.get(0).authorNickname();
    if (commonNickname == null || commonNickname.isBlank()) {
      return "";
    }
    for (AwemeProfile profile : allProfiles) {
      if (profile == null || !Objects.equals(profile.authorNickname(), commonNickname)) {
        return "";
      }
    }
    return commonNickname.trim();
  }

  private static String resolveWorkStorageTitle(List<AwemeProfile> allProfiles, String fallbackTitle) {
    if (allProfiles != null && allProfiles.size() == 1) {
      AwemeProfile profile = allProfiles.get(0);
      if (profile != null) {
        String desc = profile.desc();
        if (desc != null && !desc.isBlank()) {
          return desc.trim();
        }
        String awemeId = profile.awemeId();
        if (awemeId != null && !awemeId.isBlank()) {
          return awemeId.trim();
        }
      }
    }
    return fallbackTitle == null ? "" : fallbackTitle.trim();
  }
}
