package com.smartbox.investory.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartbox.investory.user.infrastructure.AppUserEntity;
import com.smartbox.investory.user.infrastructure.AppUserRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

class GoogleOAuth2UserServiceTest {
  @Test
  void concurrentFirstLinkMustNotOverwriteAnotherSubject() {
    AppUserRepository users = mock(AppUserRepository.class);
    AppUserEntity initial = user(null);
    when(users.findLinkedIdentity("new-subject", "test@example.invalid"))
        .thenReturn(List.of(initial));
    GoogleOAuth2UserService service = new GoogleOAuth2UserService(users);
    DefaultOAuth2UserService delegate = mock(DefaultOAuth2UserService.class);
    ReflectionTestUtils.setField(service, "delegate", delegate);
    OAuth2UserRequest request = mock(OAuth2UserRequest.class);
    when(delegate.loadUser(request))
        .thenReturn(
            new DefaultOAuth2User(
                List.of(),
                Map.of(
                    "sub", "new-subject", "email", "test@example.invalid", "email_verified", true),
                "sub"));

    assertThrows(OAuth2AuthenticationException.class, () -> service.loadUser(request));
    verify(users, never()).save(any());
  }

  @Test
  void successfulFirstLinkUsesOneConditionalUpdate() {
    AppUserRepository users = mock(AppUserRepository.class);
    when(users.findLinkedIdentity("new-subject", "test@example.invalid"))
        .thenReturn(List.of(user(null)));
    when(users.linkGoogleSubjectIfUnlinked(eq("test-user"), eq("new-subject"), any()))
        .thenReturn(1);
    GoogleOAuth2UserService service = service(users);
    assertEquals("test-user", service.loadUser(mock(OAuth2UserRequest.class)).getName());
    verify(users).linkGoogleSubjectIfUnlinked(eq("test-user"), eq("new-subject"), any());
    verify(users, never()).save(any());
  }

  @Test
  void alreadyLinkedSubjectDoesNotAttemptRelinking() {
    AppUserRepository users = mock(AppUserRepository.class);
    when(users.findLinkedIdentity("new-subject", "test@example.invalid"))
        .thenReturn(List.of(user("new-subject")));
    assertEquals("test-user", service(users).loadUser(mock(OAuth2UserRequest.class)).getName());
    verify(users, never()).linkGoogleSubjectIfUnlinked(any(), any(), any());
    verify(users, never()).save(any());
  }

  private static GoogleOAuth2UserService service(AppUserRepository users) {
    GoogleOAuth2UserService service = new GoogleOAuth2UserService(users);
    DefaultOAuth2UserService delegate = mock(DefaultOAuth2UserService.class);
    ReflectionTestUtils.setField(service, "delegate", delegate);
    when(delegate.loadUser(any()))
        .thenReturn(
            new DefaultOAuth2User(
                List.of(),
                Map.of(
                    "sub", "new-subject", "email", "test@example.invalid", "email_verified", true),
                "sub"));
    return service;
  }

  private static AppUserEntity user(String subject) {
    AppUserEntity user = new AppUserEntity();
    user.setUsername("test-user");
    user.setRole("USER");
    user.setActive(true);
    user.setGoogleSubject(subject);
    return user;
  }
}
