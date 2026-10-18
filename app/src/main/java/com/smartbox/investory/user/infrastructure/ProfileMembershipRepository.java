package com.smartbox.investory.user.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileMembershipRepository
    extends JpaRepository<ProfileMembershipEntity, ProfileMembershipId> {
  interface ProfileRow {
    Long getId();

    String getName();

    String getRole();
  }

  @Query(
      value =
          "select p.id as id, p.name as name, pm.role as role from investory.profile_memberships pm join investory.app_users u on u.id=pm.user_id join investory.portfolios p on p.id=pm.profile_id where u.username=:username and u.active order by pm.created_at, p.id",
      nativeQuery = true)
  List<ProfileRow> findAccessibleProfiles(@Param("username") String username);
}
