package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.CashReserveCommand;
import com.smartbox.investory.longterm.api.model.CashReserveView;
import com.smartbox.investory.longterm.api.model.PortfolioNotFoundException;
import com.smartbox.investory.longterm.infrastructure.cash.CashReserveEntity;
import com.smartbox.investory.longterm.infrastructure.cash.CashReserveRepository;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CashReserveCommandService {
  private final CashReserveRepository cashReserves;
  private final PortfolioContextReader portfolios;

  public CashReserveCommandService(
      CashReserveRepository cashReserves, PortfolioContextReader portfolios) {
    this.cashReserves = cashReserves;
    this.portfolios = portfolios;
  }

  public CashReserveView create(CashReserveCommand command) {
    if (command == null || command.id() != null)
      throw new IllegalArgumentException("Create requires an absent asset ID");
    return save(command);
  }

  public CashReserveView update(CashReserveCommand command) {
    if (command == null || command.id() == null)
      throw new IllegalArgumentException("Update requires an asset ID");
    return save(command);
  }

  private CashReserveView save(CashReserveCommand command) {
    if (command == null
        || command.portfolioId() == null
        || command.portfolioId() <= 0
        || (command.id() != null && command.id() <= 0)
        || command.name() == null
        || command.name().isBlank()
        || command.name().length() > 255
        || command.currency() == null
        || command.value() == null
        || command.value().signum() < 0
        || command.interestRate() == null
        || command.interestRate().signum() < 0)
      throw new IllegalArgumentException("Cash reserve facts are incomplete");
    if (command.acquisitionDate() != null
        && command.maturityDate() != null
        && command.maturityDate().isBefore(command.acquisitionDate()))
      throw new IllegalArgumentException("Cash reserve maturity must be on or after acquisition");
    if (portfolios.findById(command.portfolioId()).isEmpty())
      throw new PortfolioNotFoundException(command.portfolioId());
    CashReserveEntity reserve =
        command.id() == null
            ? new CashReserveEntity()
            : cashReserves
                .findByIdAndPortfolioId(command.id(), command.portfolioId())
                .orElseThrow(
                    () ->
                        new com.smartbox.investory.longterm.api.model.AssetNotFoundException(
                            command.portfolioId(), command.id()));
    if (reserve.getId() != null && reserve.getCurrency() != command.currency())
      throw new IllegalArgumentException("Asset currency cannot be changed after creation");
    reserve.setPortfolioId(command.portfolioId());
    reserve.setName(command.name());
    reserve.setCurrency(command.currency());
    reserve.setValue(command.value());
    reserve.setAcquisitionDate(command.acquisitionDate());
    reserve.setInterestRate(command.interestRate());
    reserve.setMaturityDate(command.maturityDate());
    reserve.setNotes(command.notes());
    return view(cashReserves.save(reserve));
  }

  @Transactional(readOnly = true)
  public Optional<CashReserveView> find(Long portfolioId, Long id) {
    return cashReserves
        .findByIdAndPortfolioId(id, portfolioId)
        .map(CashReserveCommandService::view);
  }

  private static CashReserveView view(CashReserveEntity reserve) {
    return new CashReserveView(
        reserve.getId(),
        reserve.getPortfolioId(),
        reserve.getName(),
        reserve.getCurrency(),
        reserve.getAcquisitionDate(),
        reserve.getValue(),
        reserve.getInterestRate(),
        reserve.getMaturityDate(),
        reserve.getArchivedAt() == null,
        reserve.getNotes());
  }
}
