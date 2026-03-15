package com.hhst.dydownloader.model;

import com.hhst.dydownloader.R;

public enum CardType {
  PHOTO(R.drawable.ic_photo),
  VIDEO(R.drawable.ic_video),
  ALBUM(R.drawable.ic_album),
  COLLECTION(R.drawable.ic_collection);

  private final int iconResId;

  CardType(int iconResId) {
    this.iconResId = iconResId;
  }

  public int getIconResId() {
    return iconResId;
  }
}
