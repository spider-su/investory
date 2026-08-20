package com.smartbox.investory.investment.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

/**
 * @deprecated Read/write compatibility adapter. Persistence belongs to {@link PositionRepository}.
 */
@Deprecated
@Component
@RequiredArgsConstructor
public class ClosedPositionRepository {
  private final PositionRepository positionRepository;

  public List<ClosedPosition> findAll() {
    return positionRepository.findClosed().stream().map(ClosedPositionRepository::copy).toList();
  }

  public List<ClosedPosition> findAllByAccountIn(Collection<Long> accounts) {
    return positionRepository.findClosedByAccountIn(accounts).stream()
        .map(ClosedPositionRepository::copy)
        .toList();
  }

  public List<ClosedPosition> findClosedByAssetId(Long assetId) {
    return positionRepository.findClosedByAssetId(assetId).stream()
        .map(ClosedPositionRepository::copy)
        .toList();
  }

  public void deleteByAccount(Long account) {
    positionRepository.deleteClosedByAccount(account);
  }

  public void flush() {
    positionRepository.flush();
  }

  public List<ClosedPosition> saveAll(Iterable<ClosedPosition> positions) {
    List<Position> saved = positionRepository.saveAll(toPositions(positions));
    return saved.stream().map(ClosedPositionRepository::copy).toList();
  }

  private static List<Position> toPositions(Iterable<ClosedPosition> positions) {
    java.util.ArrayList<Position> result = new java.util.ArrayList<>();
    positions.forEach(position -> result.add(copyToPosition(position)));
    return result;
  }

  private static Position copyToPosition(ClosedPosition source) {
    Position target = new Position();
    BeanUtils.copyProperties(source, target);
    return target;
  }

  private static ClosedPosition copy(Position source) {
    ClosedPosition target = new ClosedPosition();
    BeanUtils.copyProperties(source, target);
    return target;
  }
}
