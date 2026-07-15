package com.example.demo.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OpenedPositionRepository extends JpaRepository<OpenedPosition, Long> {

    @Override
    @Query("SELECT op FROM OpenedPosition op WHERE op.closeTime IS NULL")
    List<OpenedPosition> findAll();

    @Query("SELECT op FROM OpenedPosition op WHERE op.closeTime IS NULL AND op.account = :account")
    List<OpenedPosition> findAllByAccount(@Param("account") Long account);

    @Modifying
    @Query("DELETE FROM OpenedPosition op WHERE op.closeTime IS NULL AND op.account = :account")
    void deleteByAccount(@Param("account") Long account);

    @Modifying
    @Query("DELETE FROM OpenedPosition op WHERE op.closeTime IS NULL AND op.account = :account AND op NOT IN :openedPositions")
    void removeAllByAccountNotIn(@Param("account") Long account, @Param("openedPositions") List<OpenedPosition> openedPositions);
}
