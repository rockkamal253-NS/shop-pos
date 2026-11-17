package com.example.shoppos.common.format;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Central money/date formatting. Currency comes from the single {@code app.currency}
 * property (default EGP). Money is always BigDecimal scale 2 — never double/float.
 */
@Service("fmt")
public class FormatService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US);
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.US);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.US);

    private final String currency;

    public FormatService(@Value("${app.currency}") String currency) {
        this.currency = currency;
    }

    /** "1,234.50 EGP" */
    public String money(BigDecimal amount) {
        return amount == null ? "—" : number(amount) + " " + currency;
    }

    /** "1234.50" — no grouping, for receipts and CSV-ish contexts. */
    public String moneyPlain(BigDecimal amount) {
        return amount == null ? "" : amount.setScale(2).toPlainString();
    }

    public String number(BigDecimal amount) {
        return amount == null ? "—" : String.format(Locale.US, "%,.2f", amount);
    }

    public String currency() {
        return currency;
    }

    public String date(LocalDateTime value) {
        return value == null ? "—" : DATE_FMT.format(value);
    }

    public String dateTime(LocalDateTime value) {
        return value == null ? "—" : DATE_TIME_FMT.format(value);
    }

    public String time(LocalDateTime value) {
        return value == null ? "—" : TIME_FMT.format(value);
    }
}
