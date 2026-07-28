package com.example.demo.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;

@Repository
public interface OpenedPositionRepository extends JpaRepository<OpenedPosition, Long> {

    @Override
    @Query("SELECT op FROM OpenedPosition op WHERE op.closeTime IS NULL")
    List<OpenedPosition> findAll();

    @Query("SELECT op FROM OpenedPosition op WHERE op.closeTime IS NULL AND op.account = :account")
    List<OpenedPosition> findAllByAccount(@Param("account") Long account);

    @Query("SELECT op FROM OpenedPosition op WHERE op.closeTime IS NULL AND op.account IN :accounts")
    List<OpenedPosition> findAllByAccountIn(@Param("accounts") Collection<Long> accounts);

    @Modifying
    @Query("DELETE FROM OpenedPosition op WHERE op.closeTime IS NULL AND op.account = :account")
    void deleteByAccount(@Param("account") Long account);

    @Modifying
    @Query("DELETE FROM OpenedPosition op WHERE op.closeTime IS NULL AND op.account = :account AND op NOT IN :openedPositions")
    void removeAllByAccountNotIn(@Param("account") Long account, @Param("openedPositions") List<OpenedPosition> openedPositions);

    @Modifying
    @Query(
            value = """
                    DELETE FROM investory.positions p
                    WHERE p.account_id = :account
                      AND p.close_time IS NULL
                      AND p.id IN (
                          SELECT id
                          FROM (
                              SELECT p2.id,
                                     ROW_NUMBER() OVER (
                                         PARTITION BY p2.account_id,
                                                      p2.asset_id,
                                                      p2.operation,
                                                      COALESCE(p2.volume, 0),
                                                      COALESCE(p2.currency, ''),
                                                      COALESCE(p2.open_time, '-infinity'::timestamptz),
                                                      COALESCE(p2.open_price, 0),
                                                      COALESCE(p2.base_value, 0),
                                                      COALESCE(p2.purchase_value, 0),
                                                      COALESCE(p2.sale_value, 0),
                                                      COALESCE(p2.margin, 0),
                                                      COALESCE(p2.commission, 0),
                                                      COALESCE(p2.swap, 0),
                                                      COALESCE(p2.profit, 0)
                                         ORDER BY p2.id
                                     ) AS rn
                              FROM investory.positions p2
                              WHERE p2.account_id = :account
                                AND p2.close_time IS NULL
                          ) ranked
                          WHERE ranked.rn > 1
                      )
                    """,
            nativeQuery = true)
    int deleteDuplicateOpenedPositions(@Param("account") Long account);
}
