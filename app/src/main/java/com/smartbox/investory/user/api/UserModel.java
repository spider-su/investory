package com.smartbox.investory.user.api;

import com.smartbox.investory.user.infrastructure.AppUserEntity;
import java.time.LocalDate;

public record UserModel(Long id, String username, String displayName, LocalDate birthDate) {
  public static UserModel from(AppUserEntity user) {
    return new UserModel(
        user.getId(), user.getUsername(), user.getDisplayName(), user.getBirthDate());
  }
}
