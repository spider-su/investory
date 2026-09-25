package com.smartbox.investory.user.web;

import com.smartbox.investory.user.application.UserInvitationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserInvitationRestController {
  private final UserInvitationService invitations;

  @PostMapping("/api/v1/admin/user-invitations")
  public InvitationResponse create(
      @Valid @RequestBody CreateInvitationRequest request, Authentication authentication) {
    var created = invitations.create(request.email(), request.displayName(), request.profileId(), request.profileRole(), authentication.getName());
    return new InvitationResponse(created.token(), created.expiresAt());
  }

  @PostMapping("/api/v1/auth/invitations/{token}/accept")
  public void accept(@PathVariable String token, @Valid @RequestBody AcceptInvitationRequest request) {
    invitations.accept(token, request.password());
  }

  public record CreateInvitationRequest(
      @NotBlank @Email String email,
      @NotBlank String displayName,
      @Positive long profileId,
      @NotBlank String profileRole) {}

  public record AcceptInvitationRequest(@NotBlank String password) {}
  public record InvitationResponse(String token, Instant expiresAt) {}
}
