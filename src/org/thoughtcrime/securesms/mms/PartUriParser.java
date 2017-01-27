package org.thoughtcrime.securesms.mms;

import android.content.ContentUris;
import android.net.Uri;

import org.thoughtcrime.securesms.attachments.AttachmentId;

public class PartUriParser {

  private final Uri uri;

  public PartUriParser(Uri uri) {
    this.uri = uri;
  }

  public AttachmentId getPartId() {
    return new AttachmentId(getId(), getUniqueId());
  }

  private long getId() {
    try {
      return ContentUris.parseId(uri);
    } catch (NumberFormatException e) {
      return Long.parseLong(uri.getPathSegments().get(2));
    }
  }

  private long getUniqueId() {
    return Long.parseLong(uri.getPathSegments().get(1));
  }

}
