package com.smartbox.investory.user.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "profile_memberships", schema = "investory")
@IdClass(ProfileMembershipId.class)
@Getter
@Setter
public class ProfileMembershipEntity {
  @Id
  @Column(name = "user_id")
  private Long userId;

  @Id
  @Column(name = "profile_id")
  private Long profileId;

  private String role;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
