package com.example.demo.infrastructure.repository.account;

import com.example.demo.infrastructure.CurrencyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "accounts")
public class Account {

  @Id private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "currency", nullable = false)
  private CurrencyType currency;

  @Column(name = "provider", nullable = false)
  private String provider;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "owner", nullable = false)
  private String owner;

  @Column(name = "portfolio_id", nullable = false)
  private Long portfolioId;

  @Column(name = "cash_only", nullable = false)
  private boolean cashOnly;
}
