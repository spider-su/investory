
package com.example.demo.data.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OpenedPositionRepository extends JpaRepository<OpenedPosition, Long> {

    List<OpenedPosition> findAllByAccount(String account);

    @Modifying
    @Query("DELETE FROM OpenedPosition op WHERE op.account = :account AND op NOT IN :openedPositions")
    void removeAllByAccountNotIn(@Param("account") String account, @Param("openedPositions") List<OpenedPosition> openedPositions);
}
