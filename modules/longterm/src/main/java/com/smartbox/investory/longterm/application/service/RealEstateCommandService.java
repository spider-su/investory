package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.PortfolioNotFoundException;
import com.smartbox.investory.longterm.api.model.RealEstateCommand;
import com.smartbox.investory.longterm.api.model.RealEstateView;
import com.smartbox.investory.longterm.infrastructure.realestate.RealEstateEntity;
import com.smartbox.investory.longterm.infrastructure.realestate.RealEstateRepository;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RealEstateCommandService {
  private final RealEstateRepository realEstates;
  private final PortfolioContextReader portfolios;
  private final com.smartbox.investory.longterm.infrastructure.lifecycle
          .LongTermAssetHistoryRepository
      history;

  public RealEstateCommandService(
      RealEstateRepository realEstates,
      PortfolioContextReader portfolios,
      com.smartbox.investory.longterm.infrastructure.lifecycle.LongTermAssetHistoryRepository
          history) {
    this.realEstates = realEstates;
    this.portfolios = portfolios;
    this.history = history;
  }

  public RealEstateView create(RealEstateCommand command) {
    if (command == null || command.id() != null)
      throw new IllegalArgumentException("Create requires an absent asset ID");
    return save(command);
  }

  public RealEstateView update(RealEstateCommand command) {
    if (command == null || command.id() == null)
      throw new IllegalArgumentException("Update requires an asset ID");
    return save(command);
  }

  private RealEstateView save(RealEstateCommand command) {
    if (command == null
        || command.portfolioId() == null
        || command.portfolioId() <= 0
        || (command.id() != null && command.id() <= 0)
        || command.name() == null
        || command.name().isBlank()
        || command.name().length() > 255
        || command.currency() == null
        || command.value() == null
        || command.value().signum() < 0)
      throw new IllegalArgumentException("Real-estate facts are incomplete");
    if (command.taxBase() != null && command.taxBase().signum() < 0)
      throw new IllegalArgumentException("Real-estate tax base cannot be negative");
    if (command.landRegisterNumber() != null && command.landRegisterNumber().length() > 128)
      throw new IllegalArgumentException("Land register number is too long");
    if (portfolios.findById(command.portfolioId()).isEmpty())
      throw new PortfolioNotFoundException(command.portfolioId());
    RealEstateEntity estate =
        command.id() == null
            ? new RealEstateEntity()
            : realEstates
                .findByIdAndPortfolioId(command.id(), command.portfolioId())
                .orElseThrow(
                    () ->
                        new com.smartbox.investory.longterm.api.model.AssetNotFoundException(
                            command.portfolioId(), command.id()));
    if (estate.getId() != null && estate.getCurrency() != command.currency())
      throw new IllegalArgumentException("Asset currency cannot be changed after creation");
    estate.setPortfolioId(command.portfolioId());
    estate.setName(command.name());
    estate.setCurrency(command.currency());
    estate.setValue(command.value());
    estate.setTaxBase(command.taxBase());
    estate.setAcquisitionDate(command.acquisitionDate());
    estate.setLandRegisterNumber(command.landRegisterNumber());
    estate.setNotes(command.notes());
    var saved = realEstates.save(estate);
    if (command.id() == null) history.recordCreation(saved.getId(), "REAL_ESTATE");
    return view(saved);
  }

  @Transactional(readOnly = true)
  public Optional<RealEstateView> find(Long portfolioId, Long id) {
    return realEstates.findByIdAndPortfolioId(id, portfolioId).map(RealEstateCommandService::view);
  }

  private static RealEstateView view(RealEstateEntity estate) {
    return new RealEstateView(
        estate.getId(),
        estate.getPortfolioId(),
        estate.getName(),
        estate.getCurrency(),
        estate.getAcquisitionDate(),
        estate.getValue(),
        estate.getTaxBase(),
        estate.getLandRegisterNumber(),
        estate.getArchivedAt() == null,
        estate.getNotes());
  }
}
