package com.example.demo.infrastructure.repository;

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

/** @deprecated Read/write compatibility adapter. Persistence belongs to {@link PositionRepository}. */
@Deprecated
@Component
@RequiredArgsConstructor
public class OpenedPositionRepository {
  private final PositionRepository positionRepository;

  public List<OpenedPosition> findAll() { return positionRepository.findOpen().stream().map(OpenedPositionRepository::copy).toList(); }
  public List<OpenedPosition> findAllByAccount(Long account) { return positionRepository.findOpenByAccount(account).stream().map(OpenedPositionRepository::copy).toList(); }
  public List<OpenedPosition> findAllByAccountIn(Collection<Long> accounts) { return positionRepository.findOpenByAccountIn(accounts).stream().map(OpenedPositionRepository::copy).toList(); }
  public List<OpenedPosition> findOpenByAssetId(Long assetId) { return positionRepository.findOpenByAssetId(assetId).stream().map(OpenedPositionRepository::copy).toList(); }
  public void deleteByAccount(Long account) { positionRepository.deleteOpenByAccount(account); }
  public void flush() { positionRepository.flush(); }
  public void removeAllByAccountNotIn(Long account, List<OpenedPosition> positions) { positionRepository.removeOpenByAccountNotIn(account, List.copyOf(positions)); }
  public List<OpenedPosition> saveAll(Iterable<OpenedPosition> positions) { List<Position> saved = positionRepository.saveAll(toPositions(positions)); return saved.stream().map(OpenedPositionRepository::copy).toList(); }

  private static List<Position> toPositions(Iterable<OpenedPosition> positions) { java.util.ArrayList<Position> result = new java.util.ArrayList<>(); positions.forEach(position -> result.add(copyToPosition(position))); return result; }
  private static Position copyToPosition(OpenedPosition source) { Position target = new Position(); BeanUtils.copyProperties(source, target); return target; }
  private static OpenedPosition copy(Position source) { OpenedPosition target = new OpenedPosition(); BeanUtils.copyProperties(source, target); return target; }
}
