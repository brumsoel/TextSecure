package org.thoughtcrime.securesms.database.documents;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedList;
import java.util.List;

public class UnregisteredUserList implements Document<UnregisteredUser> {

  @JsonProperty(value = "l")
  private List<UnregisteredUser> users;

  public UnregisteredUserList() {
    this.users = new LinkedList<>();
  }

  public UnregisteredUserList(List<UnregisteredUser> users) {
    this.users = users;
  }

  @Override
  public int size() {
    if (users == null) return 0;
    else               return users.size();
  }

  @Override
  @JsonIgnore
  public List<UnregisteredUser> getList() {
    return users;
  }
}
