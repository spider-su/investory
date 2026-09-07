package com.smartbox.investory.longterm.infrastructure.lifecycle;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** Long-Term-owned provenance; absent legacy rows never imply complete history. */
@Repository
public class LongTermAssetHistoryRepository {
  @PersistenceContext private EntityManager entityManager;

  public void recordCreation(Long assetId, String type) {
    entityManager
        .createNativeQuery(
            "insert into long_term_asset_history(asset_id, asset_type, complete) values (:id, :type, true)")
        .setParameter("id", assetId)
        .setParameter("type", type)
        .executeUpdate();
  }

  public Set<Long> completeRealEstateIds(Collection<Long> assetIds) {
    if (assetIds.isEmpty()) return Set.of();
    List<?> ids =
        entityManager
            .createNativeQuery(
                "select asset_id from long_term_asset_history where complete = true and asset_type = 'REAL_ESTATE' and asset_id in (:ids)")
            .setParameter("ids", assetIds)
            .getResultList();
    return ids.stream().map(id -> ((Number) id).longValue()).collect(Collectors.toSet());
  }

  public List<ArchiveInterval> intervals(Collection<Long> assetIds) {
    if (assetIds.isEmpty()) return List.of();
    List<?> rows =
        entityManager
            .createNativeQuery(
                "select asset_id, archived_from, archived_to from long_term_asset_archive_interval where asset_id in (:ids) order by asset_id, archived_from")
            .setParameter("ids", assetIds)
            .getResultList();
    return rows.stream()
        .map(
            value -> {
              Object[] row = (Object[]) value;
              return new ArchiveInterval(((Number) row[0]).longValue(), date(row[1]), date(row[2]));
            })
        .toList();
  }

  public void transition(
      Long assetId,
      String type,
      LocalDate previousArchive,
      LocalDate nextArchive,
      LocalDate today) {
    entityManager
        .createNativeQuery(
            "insert into long_term_asset_history(asset_id, asset_type, complete) values (:id, :type, false) on conflict (asset_id) do nothing")
        .setParameter("id", assetId)
        .setParameter("type", type)
        .executeUpdate();
    // Serialize interval changes for this identity within the caller's transaction.
    entityManager
        .createNativeQuery(
            "select asset_id from long_term_asset_history where asset_id = :id for update")
        .setParameter("id", assetId)
        .getSingleResult();
    if (nextArchive != null) {
      entityManager
          .createNativeQuery(
              "insert into long_term_asset_archive_interval(asset_id, archived_from) values (:id, :date)")
          .setParameter("id", assetId)
          .setParameter("date", nextArchive)
          .executeUpdate();
    } else {
      int closed =
          entityManager
              .createNativeQuery(
                  "update long_term_asset_archive_interval set archived_to = :date where asset_id = :id and archived_to is null")
              .setParameter("id", assetId)
              .setParameter("date", today)
              .executeUpdate();
      if (closed == 0) {
        entityManager
            .createNativeQuery(
                "insert into long_term_asset_archive_interval(asset_id, archived_from, archived_to) values (:id, :start, :end)")
            .setParameter("id", assetId)
            .setParameter("start", previousArchive)
            .setParameter("end", today)
            .executeUpdate();
      }
    }
  }

  private static LocalDate date(Object value) {
    return value == null
        ? null
        : value instanceof LocalDate date ? date : ((java.sql.Date) value).toLocalDate();
  }

  /** Half-open interval: archive day excluded, reactivation day included in income. */
  public record ArchiveInterval(Long assetId, LocalDate from, LocalDate to) {}
}
