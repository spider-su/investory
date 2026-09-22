package com.smartbox.investory.ryczalt.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RyczaltZusRuleSetJpaRepository
    extends JpaRepository<RyczaltZusRuleSetEntity, Long> {
  Optional<RyczaltZusRuleSetEntity> findByYear(int year);

  Optional<RyczaltZusRuleSetEntity> findFirstByYearLessThanEqualOrderByYearDesc(int year);
}
