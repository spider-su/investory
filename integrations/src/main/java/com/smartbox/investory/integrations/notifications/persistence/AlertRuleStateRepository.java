package com.smartbox.investory.integrations.notifications.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRuleStateRepository extends JpaRepository<AlertRuleStateEntity, String> {

  List<AlertRuleStateEntity> findAllByRuleCodeOrRuleCodeStartingWith(
      String ruleCode, String ruleCodePrefix);
}
