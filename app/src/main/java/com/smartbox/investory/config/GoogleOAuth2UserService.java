package com.smartbox.investory.config;

import com.smartbox.investory.user.infrastructure.AppUserEntity;
import com.smartbox.investory.user.infrastructure.AppUserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/** Links an already provisioned application user to one verified Google identity. */
@Service
public class GoogleOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
  private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
  private final AppUserRepository users;

  public GoogleOAuth2UserService(AppUserRepository users) {
    this.users = users;
  }

  @Override
  public OAuth2User loadUser(OAuth2UserRequest request) {
    OAuth2User googleUser = delegate.loadUser(request);
    String subject = required(googleUser.getAttribute("sub"));
    String email = required(googleUser.getAttribute("email"));
    Boolean emailVerified = googleUser.getAttribute("email_verified");
    if (!Boolean.TRUE.equals(emailVerified)) {
      throw authenticationFailure("Google email is not verified");
    }

    LinkedUser linked = findBySubjectOrEmail(subject, email);
    if (linked == null) {
      throw authenticationFailure(
          "Google account is not linked to an active Investory user: " + email);
    }
    if (linked.subject() == null) {
      if (users
          .findByUsernameAndActiveTrue(linked.username())
          .filter(u -> u.getGoogleSubject() == null)
          .isEmpty()) {
        throw authenticationFailure("Google account could not be linked safely");
      }
      AppUserEntity entity = users.findByUsernameAndActiveTrue(linked.username()).orElseThrow();
      entity.setGoogleSubject(subject);
      users.save(entity);
    }

    Map<String, Object> attributes = new HashMap<>(googleUser.getAttributes());
    attributes.put("username", linked.username());
    List<GrantedAuthority> authorities =
        List.of(new SimpleGrantedAuthority("ROLE_" + normalizeRole(linked.role())));
    return new DefaultOAuth2User(authorities, attributes, "username");
  }

  private LinkedUser findBySubjectOrEmail(String subject, String email) {
    return users.findLinkedIdentity(subject, email).stream()
        .findFirst()
        .map(u -> new LinkedUser(u.getUsername(), u.getRole(), u.getGoogleSubject()))
        .orElse(null);
  }

  private static String required(Object value) {
    if (value instanceof String string && !string.isBlank()) return string;
    throw authenticationFailure("Google did not return a required identity claim");
  }

  private static String normalizeRole(String role) {
    return role == null || role.isBlank() ? "USER" : role;
  }

  private static OAuth2AuthenticationException authenticationFailure(String message) {
    return new OAuth2AuthenticationException(new OAuth2Error("invalid_google_identity"), message);
  }

  private record LinkedUser(String username, String role, String subject) {}
}
