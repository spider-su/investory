package com.smartbox.investory.integrations.notifications.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEventRepository extends JpaRepository<NotificationEventEntity, Long> {
  List<NotificationEventEntity>
      findTop50ByDeliveryStateInAndAttemptCountLessThanAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
          Collection<NotificationDeliveryState> states, int maxAttempts, Instant now);
}
