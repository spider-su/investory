package com.smartbox.investory.user.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserInvitationRepository extends JpaRepository<UserInvitationEntity, Long> {
  @Query(
      value = "select * from investory.app_user_invitations where token_hash=:hash for update",
      nativeQuery = true)
  Optional<UserInvitationEntity> findByTokenHashForUpdate(@Param("hash") String hash);

  @Modifying
  @Query(
      value =
          "update investory.app_user_invitations set consumed_at=now() where id=:id and consumed_at is null and expires_at >= now()",
      nativeQuery = true)
  int consumeIfAvailable(@Param("id") long id);
}
