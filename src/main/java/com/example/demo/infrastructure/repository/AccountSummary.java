package com.example.demo.infrastructure.repository;

import com.example.demo.infrastructure.CurrencyType;
import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

/**
 * Per-account snapshot taken from the broker export header (Balance / Equity row).
 * {@code equity} represents the total value of all assets on the account as of the
 * export generation time (free cash + market value of open positions).
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(of = "account")
@Table(name = "account_summaries")
public class AccountSummary {

    @Id
    private String account;

    @Enumerated(value = EnumType.STRING)
    private CurrencyType currency;

    /** Free cash balance reported by the broker. */
    private Double balance;

    /** Total assets value (cash + open positions) reported by the broker. */
    private Double equity;

    private ZonedDateTime updatedAt;
}

