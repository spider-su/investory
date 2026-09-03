package com.smartbox.investory.integrations.notifications.application;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
public class NotificationAdminRestController {
  private final NotificationAdminService notifications;

  @GetMapping
  public List<NotificationAdminService.NotificationEventView> list() {
    return notifications.list();
  }

  @GetMapping("/summary")
  public NotificationAdminService.NotificationQueueSummary summary() {
    return notifications.summary();
  }

  @PostMapping("/{id}/replay")
  public ResponseEntity<Void> replay(@PathVariable long id) {
    return notifications.replay(id)
        ? ResponseEntity.accepted().build()
        : ResponseEntity.notFound().build();
  }
}
