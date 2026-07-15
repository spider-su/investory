package com.example.demo.infrastructure.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ClosedPositionRepository extends JpaRepository<ClosedPosition, Long> {

	@Override
	@Query("SELECT cp FROM ClosedPosition cp WHERE cp.closeTime IS NOT NULL")
	List<ClosedPosition> findAll();
}
