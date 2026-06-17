package com.example.demo.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportRowErrorRepository extends JpaRepository<ImportRowError, Long> {
	List<ImportRowError> findAllByBatch_IdOrderByIdAsc(Long batchId);
}

