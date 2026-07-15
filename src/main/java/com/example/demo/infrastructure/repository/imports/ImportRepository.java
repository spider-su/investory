package com.example.demo.infrastructure.repository.imports;

import com.example.demo.infrastructure.BrokerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImportRepository extends JpaRepository<ImportHistory, Long> {
	Optional<ImportHistory> findFirstByBrokerAndFileSha256OrderByIdDesc(BrokerType broker, String fileSha256);

	Optional<ImportHistory> findFirstByOrderByIdDesc();

	@SuppressWarnings("deprecation")
	default ImportHistory getById(Long id) {
		return findById(id)
				.orElseThrow(() -> new IllegalStateException("Import batch missing: " + id));
	}
}

