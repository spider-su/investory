package com.example.demo.data.repository;

import com.example.demo.data.BrokerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
	Optional<ImportBatch> findFirstByBrokerAndFileSha256OrderByIdDesc(BrokerType broker, String fileSha256);

	Optional<ImportBatch> findFirstByOrderByIdDesc();
}

