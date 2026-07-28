package com.example.demo.infrastructure.repository;

import com.example.demo.infrastructure.CurrencyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "assets")
public class Asset {

  @Id
  @SequenceGenerator(name = "assets_id_seq", sequenceName = "assets_id_seq", allocationSize = 50)
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "assets_id_seq")
  @Column(name = "id", updatable = false, nullable = false)
  private Long id;

  @Column(name = "name", nullable = false, length = 511)
  private String name;

  @Column(name = "symbol", nullable = false, length = 64)
  private String symbol;

  @Column(name = "ticker", nullable = false, length = 64)
  private String ticker;

  @Column(name = "ibkr", nullable = false, length = 64)
  private String ibrk;

  @Column(name = "yahoo", nullable = false, length = 64)
  private String yahoo;

  @Column(name = "country", nullable = false, length = 15)
  private String country;

  @Enumerated(EnumType.STRING)
  @Column(name = "currency", nullable = false, length = 3)
  private CurrencyType currency;

  @Column(name = "asset_type", nullable = false, length = 32)
  private String assetType;

  @Column(name = "isin", length = 12)
  private String isin;

  @Column(name = "figi", length = 16)
  private String figi;

  @Column(name = "exchange_mic", length = 4)
  private String exchangeMic;

  @Column(name = "active")
  private Boolean active;

  @Column(name = "market_price")
  private Double marketPrice;

  @Column(name = "market_price_usd")
  private Double marketPriceUsd;

  @Column(name = "price_source")
  private String priceSource;

  @Column(name = "price_updated_at")
  private ZonedDateTime priceUpdatedAt;
}

