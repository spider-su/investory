package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.BondCommand;
import com.smartbox.investory.longterm.api.model.BondView;
import com.smartbox.investory.longterm.api.model.PortfolioNotFoundException;
import com.smartbox.investory.longterm.infrastructure.bond.BondEntity;
import com.smartbox.investory.longterm.infrastructure.bond.BondRepository;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BondCommandService {
  private final BondRepository bonds;
  private final PortfolioContextReader portfolios;

  public BondCommandService(BondRepository bonds, PortfolioContextReader portfolios) {
    this.bonds = bonds;
    this.portfolios = portfolios;
  }

  public BondView create(BondCommand command) {
    if (command == null || command.id() != null)
      throw new IllegalArgumentException("Create requires an absent asset ID");
    return save(command);
  }

  public BondView update(BondCommand command) {
    if (command == null || command.id() == null)
      throw new IllegalArgumentException("Update requires an asset ID");
    return save(command);
  }

  private BondView save(BondCommand command) {
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
        || command.interestRate().signum() < 0
        || command.maturityDate() == null)
      throw new IllegalArgumentException("Bond facts are incomplete");
    if (command.acquisitionDate() != null
        && command.maturityDate().isBefore(command.acquisitionDate()))
      throw new IllegalArgumentException("Bond maturity must be on or after acquisition");
    if (portfolios.findById(command.portfolioId()).isEmpty())
      throw new PortfolioNotFoundException(command.portfolioId());
    BondEntity bond =
        command.id() == null
            ? new BondEntity()
            : bonds
                .findByIdAndPortfolioId(command.id(), command.portfolioId())
                .orElseThrow(
                    () ->
                        new com.smartbox.investory.longterm.api.model.AssetNotFoundException(
                            command.portfolioId(), command.id()));
    if (bond.getId() != null && bond.getCurrency() != command.currency())
      throw new IllegalArgumentException("Asset currency cannot be changed after creation");
    bond.setPortfolioId(command.portfolioId());
    bond.setName(command.name());
    bond.setCurrency(command.currency());
    bond.setValue(command.value());
    bond.setAcquisitionDate(command.acquisitionDate());
    bond.setInterestRate(command.interestRate());
    bond.setMaturityDate(command.maturityDate());
    bond.setNotes(command.notes());
    BondEntity saved = bonds.save(bond);
    return new BondView(
        saved.getId(),
        saved.getPortfolioId(),
        saved.getName(),
        saved.getCurrency(),
        saved.getAcquisitionDate(),
        saved.getValue(),
        saved.getInterestRate(),
        saved.getMaturityDate(),
        saved.getArchivedAt() == null,
        saved.getNotes());
  }

  @Transactional(readOnly = true)
  public Optional<BondView> find(Long portfolioId, Long id) {
    return bonds
        .findByIdAndPortfolioId(id, portfolioId)
        .map(
            bond ->
                new BondView(
                    bond.getId(),
                    bond.getPortfolioId(),
                    bond.getName(),
                    bond.getCurrency(),
                    bond.getAcquisitionDate(),
                    bond.getValue(),
                    bond.getInterestRate(),
                    bond.getMaturityDate(),
                    bond.getArchivedAt() == null,
                    bond.getNotes()));
  }
}
