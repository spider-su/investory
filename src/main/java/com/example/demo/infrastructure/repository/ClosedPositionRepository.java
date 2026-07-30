package com.example.demo.infrastructure.repository;

import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClosedPositionRepository extends JpaRepository<ClosedPosition, Long> {

	@Override
	@Query("SELECT cp FROM ClosedPosition cp WHERE cp.closeTime IS NOT NULL")
	List<ClosedPosition> findAll();

	@Query("SELECT cp FROM ClosedPosition cp WHERE cp.closeTime IS NOT NULL AND cp.account IN :accounts")
	List<ClosedPosition> findAllByAccountIn(@Param("accounts") Collection<Long> accounts);

	@Modifying
	@Query("DELETE FROM ClosedPosition cp WHERE cp.closeTime IS NOT NULL AND cp.account = :account")
	void deleteByAccount(@Param("account") Long account);

	@Modifying
	@Query(
			value = """
					DELETE FROM investory.positions p
					WHERE p.account_id = :account
					  AND p.close_time IS NOT NULL
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
					                                  COALESCE(p2.close_time, 'infinity'::timestamptz),
					                                  COALESCE(p2.close_price, 0),
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
					            AND p2.close_time IS NOT NULL
					      ) ranked
					      WHERE ranked.rn > 1
					  )
					""",
			nativeQuery = true)
	int deleteDuplicateClosedPositions(@Param("account") Long account);
}
