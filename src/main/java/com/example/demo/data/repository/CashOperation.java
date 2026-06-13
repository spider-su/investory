package com.example.demo.data.repository;

import com.example.demo.data.CashOperationType;
import com.example.demo.data.CurrencyType;
import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EqualsAndHashCode(of = "id")
@Table(name = "cash_operations")
public class CashOperation {

    @Id
    private Long id;

    private String account;

    @Enumerated(value = EnumType.STRING)
    private CashOperationType type;

    private String symbol;

    private Double amount;

    @Enumerated(value = EnumType.STRING)
    private CurrencyType currency;

    private String comment;

    private ZonedDateTime date;

}
