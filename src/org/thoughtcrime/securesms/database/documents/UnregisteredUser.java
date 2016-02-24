package org.thoughtcrime.securesms.database.documents;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UnregisteredUser {

  @JsonProperty(value = "r")
  private long recipientId;

  public UnregisteredUser(long recipientId) {
    this.recipientId = recipientId;
  }

  public UnregisteredUser() {}

  public long getRecipientId() {
    return recipientId;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof UnregisteredUser)) return false;

    UnregisteredUser that = (UnregisteredUser)other;
    return this.recipientId == that.recipientId;
  }

  @Override
  public int hashCode() {
    return (int)recipientId;
  }
}
