package com.example.demo.infrastructure.repository.account;

import com.example.demo.infrastructure.CurrencyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "accounts")
public class Account {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "external_account_id", nullable = false, length = 128)
  private String externalAccountId;

  @Enumerated(EnumType.STRING)
  @Column(name = "currency", nullable = false, length = 3)
  private CurrencyType currency;

  @Column(name = "provider", nullable = false, length = 7)
  private String provider;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "owner", nullable = false, length = 255)
  private String owner;

  @Column(name = "portfolio_id", nullable = false)
  private Long portfolioId;

  @Column(name = "cash_only", nullable = false)
  private boolean cashOnly;
}
