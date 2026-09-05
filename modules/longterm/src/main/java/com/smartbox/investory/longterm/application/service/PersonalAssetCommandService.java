package com.smartbox.investory.longterm.application.service;

import com.smartbox.investory.longterm.api.model.PersonalAssetCategory;
import com.smartbox.investory.longterm.api.model.PersonalAssetCommand;
import com.smartbox.investory.longterm.api.model.PersonalAssetView;
import com.smartbox.investory.longterm.api.model.PortfolioNotFoundException;
import com.smartbox.investory.longterm.infrastructure.personal.PersonalAssetEntity;
import com.smartbox.investory.longterm.infrastructure.personal.PersonalAssetRepository;
import com.smartbox.investory.shared.portfolio.PortfolioContextReader;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PersonalAssetCommandService {
  private final PersonalAssetRepository assets;
  private final PortfolioContextReader portfolios;

  public PersonalAssetCommandService(
      PersonalAssetRepository assets, PortfolioContextReader portfolios) {
    this.assets = assets;
    this.portfolios = portfolios;
  }

  public PersonalAssetView create(PersonalAssetCommand command) {
    if (command == null || command.id() != null)
      throw new IllegalArgumentException("Create requires an absent asset ID");
    return save(command);
  }

  public PersonalAssetView update(PersonalAssetCommand command) {
    if (command == null || command.id() == null)
      throw new IllegalArgumentException("Update requires an asset ID");
    return save(command);
  }

  private PersonalAssetView save(PersonalAssetCommand command) {
    if (command == null
        || command.portfolioId() == null
        || command.portfolioId() <= 0
        || (command.id() != null && command.id() <= 0)
        || command.name() == null
        || command.name().isBlank()
        || command.name().length() > 255
        || command.category() == null
        || command.currency() == null
        || command.value() == null
        || command.value().signum() < 0)
      throw new IllegalArgumentException("Personal asset facts are incomplete");
    if (portfolios.findById(command.portfolioId()).isEmpty())
      throw new PortfolioNotFoundException(command.portfolioId());
    PersonalAssetEntity asset =
        command.id() == null
            ? new PersonalAssetEntity()
            : assets
                .findByIdAndPortfolioId(command.id(), command.portfolioId())
                .orElseThrow(
                    () ->
                        new com.smartbox.investory.longterm.api.model.AssetNotFoundException(
                            command.portfolioId(), command.id()));
    if (asset.getId() != null && asset.getCurrency() != command.currency())
      throw new IllegalArgumentException("Asset currency cannot be changed after creation");
    asset.setPortfolioId(command.portfolioId());
    asset.setName(command.name());
    asset.setCategory(PersonalAssetEntity.Category.valueOf(command.category().name()));
    asset.setCurrency(command.currency());
    asset.setValue(command.value());
    asset.setAcquisitionDate(command.acquisitionDate());
    asset.setNotes(command.notes());
    PersonalAssetEntity saved = assets.save(asset);
    return new PersonalAssetView(
        saved.getId(),
        saved.getPortfolioId(),
        saved.getName(),
        PersonalAssetCategory.valueOf(saved.getCategory().name()),
        saved.getCurrency(),
        saved.getAcquisitionDate(),
        saved.getValue(),
        saved.getArchivedAt() == null,
        saved.getNotes());
  }

  @Transactional(readOnly = true)
  public Optional<PersonalAssetView> find(Long portfolioId, Long id) {
    return assets.findByIdAndPortfolioId(id, portfolioId).map(PersonalAssetCommandService::view);
  }

  private static PersonalAssetView view(PersonalAssetEntity asset) {
    return new PersonalAssetView(
        asset.getId(),
        asset.getPortfolioId(),
        asset.getName(),
        PersonalAssetCategory.valueOf(asset.getCategory().name()),
        asset.getCurrency(),
        asset.getAcquisitionDate(),
        asset.getValue(),
        asset.getArchivedAt() == null,
        asset.getNotes());
  }
}
