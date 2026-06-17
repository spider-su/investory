package com.example.demo.infrastructure.repository;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(of = "id")
@Table(name = "technical_indicators")
public class TechnicalIndicator {
    @Id
    @SequenceGenerator(name = "technical_indicators_id_seq", sequenceName = "technical_indicators_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="technical_indicators_id_seq")
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;
    private String symbol;
    private double macd;
    private double rsi;
    private long volume;
    private ZonedDateTime timestamp;
    private ZonedDateTime syncDate; // for time-series
}
