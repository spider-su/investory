package com.smartbox.investory.user.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {
  Optional<AppUserEntity> findByUsernameAndActiveTrue(String username);

  boolean existsByUsernameIgnoreCase(String username);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(
      "update AppUserEntity u set u.googleSubject = :subject, u.updatedAt = :updatedAt "
          + "where u.username = :username and u.active = true and u.googleSubject is null")
  int linkGoogleSubjectIfUnlinked(
      @Param("username") String username,
      @Param("subject") String subject,
      @Param("updatedAt") Instant updatedAt);

  @Query(
      "select u from AppUserEntity u where u.active = true and (u.googleSubject = :subject or lower(u.email) = lower(:email)) order by case when u.googleSubject = :subject then 0 else 1 end")
  List<AppUserEntity> findLinkedIdentity(
      @Param("subject") String subject, @Param("email") String email);
}
