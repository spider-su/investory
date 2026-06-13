package com.example.demo.data.repository;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(of = "id")
@Table(name = "fundamental_indicators")
public class FundamentalIndicator {

    @Id
    @SequenceGenerator(name = "fundamental_indicators_id_seq", sequenceName = "fundamental_indicators_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="fundamental_indicators_id_seq")
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;
    private String symbol;
    private Double peRatio;
    private Double eps;
    private Double dividendYield;
    private ZonedDateTime syncDate;
}
