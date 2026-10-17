package com.smartbox.investory.user.infrastructure;

import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class ProfileMembershipId implements Serializable {
  private Long userId;
  private Long profileId;
}
