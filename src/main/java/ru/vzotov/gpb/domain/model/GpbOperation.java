package ru.vzotov.gpb.domain.model;

import org.apache.commons.lang.Validate;
import org.apache.commons.lang.builder.EqualsBuilder;
import ru.vzotov.accounting.domain.model.AccountReportOperation;
import ru.vzotov.ddd.shared.ValueObject;

import java.time.LocalDateTime;
import java.util.Objects;

public record GpbOperation(LocalDateTime operationDate, String accountNumber, String cardNumber, Double operationAmount,
                           String operationCurrency, String description,
                           boolean hold) implements ValueObject<GpbOperation>, AccountReportOperation {

    public GpbOperation {
        Validate.notNull(operationDate);
        Validate.notNull(operationAmount);
        Validate.notEmpty(operationCurrency);
        Validate.notNull(description);

    }

    @Override
    public boolean sameValueAs(GpbOperation that) {
        return that != null && new EqualsBuilder().
                append(operationDate, that.operationDate).
                append(operationAmount, that.operationAmount).
                append(operationCurrency, that.operationCurrency).
                append(description, that.description).
                append(hold, that.hold).
                isEquals();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GpbOperation that = (GpbOperation) o;
        return sameValueAs(that);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operationDate
                , operationAmount
                , operationCurrency
                , description
                , hold
        );
    }

    @Override
    public String toString() {
        return "GpbOperation{" +
                "date=" + operationDate +
                ", card=" + cardNumber +
                ", account=" + accountNumber +
                ", amount=" + operationAmount +
                ", currency=" + operationCurrency +
                ", description=" + description +
                ", hold=" + hold +
                '}';
    }
}
