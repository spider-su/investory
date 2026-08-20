package com.smartbox.investory.integration.notifications.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DrawdownAlertStateRepository extends JpaRepository<DrawdownAlertState, Long> {}
