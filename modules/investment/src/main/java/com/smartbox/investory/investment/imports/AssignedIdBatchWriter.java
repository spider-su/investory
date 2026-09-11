package com.smartbox.investory.investment.imports;

import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

/** Persists importer rows with stable, assigned IDs without merge-per-row existence reads. */
@Service
public class AssignedIdBatchWriter {
  private final EntityManager entityManager;

  public AssignedIdBatchWriter(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  public <T> void saveAll(
      JpaRepository<T, Long> repository, Collection<T> entities, Function<T, Long> idExtractor) {
    if (entities.isEmpty()) return;
    List<Long> ids = entities.stream().map(idExtractor).filter(java.util.Objects::nonNull).toList();
    Set<Long> existingIds =
        ids.isEmpty()
            ? Set.of()
            : new HashSet<>(repository.findAllById(ids).stream().map(idExtractor).toList());
    for (T entity : entities) {
      Long id = idExtractor.apply(entity);
      if (id == null || !existingIds.contains(id)) {
        entityManager.persist(entity);
      } else {
        // The preload made the row managed, so merge copies state without another SELECT.
        entityManager.merge(entity);
      }
    }
  }
}
