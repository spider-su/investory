package com.smartbox.investory.user.application;

import com.smartbox.investory.user.api.UserModel;
import com.smartbox.investory.user.infrastructure.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserQueryService {
  private final AppUserRepository users;

  public UserModel find(Long id) {
    return users.findById(id).map(UserModel::from).orElseThrow();
  }
}
