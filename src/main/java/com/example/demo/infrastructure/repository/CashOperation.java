package com.example.demo.infrastructure.repository;

import com.example.demo.infrastructure.CashOperationType;
import com.example.demo.infrastructure.CurrencyType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Column(name = "account_id")
    private Long account;

    @Enumerated(value = EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "operation", columnDefinition = "cash_operation_type")
    private CashOperationType type;

    @Column(name = "asset_id")
    private String symbol;

    private Double amount;

    @Enumerated(value = EnumType.STRING)
    @Column(name = "currency")
    private CurrencyType currency;

    private String comment;

    private ZonedDateTime date;

}
