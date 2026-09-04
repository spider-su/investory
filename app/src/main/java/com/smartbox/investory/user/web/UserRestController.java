package com.smartbox.investory.user.web;

import com.smartbox.investory.user.api.UserModel;
import com.smartbox.investory.user.application.UserQueryService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserRestController {
  private final UserQueryService users;

  @GetMapping("/{id}")
  public UserModel user(@PathVariable @Positive Long id) {
    return users.find(id);
  }
}
