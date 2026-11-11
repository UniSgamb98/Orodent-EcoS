package com.orodent.ecos.crm.feature.calendario.service;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.query.CalendarioQuery;
import com.orodent.ecos.crm.core.database.query.ClientiFilterQuery;
import com.orodent.ecos.crm.core.database.query.derby.DerbyCalendarioQuery;
import com.orodent.ecos.crm.core.database.query.derby.DerbyClientiFilterQuery;
import com.orodent.ecos.crm.core.database.query.result.OperatoreClienteFilterResult;
import com.orodent.ecos.crm.feature.calendario.dto.CalendarioCall;
import com.orodent.ecos.crm.feature.calendario.dto.CalendarioMonth;
import com.orodent.ecos.crm.feature.calendario.dto.CalendarioWeek;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.OperatoreFilter;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class CalendarioService {

    private static final Locale ITALIAN = Locale.ITALIAN;

    private final Clock clock;
    private final CalendarioQuery calendarioQuery;
    private final ClientiFilterQuery clientiFilterQuery;

    public CalendarioService() {
        this(Clock.systemDefaultZone());
    }

    public CalendarioService(Database database) {
        this(
                Clock.systemDefaultZone(),
                new DerbyCalendarioQuery(database),
                new DerbyClientiFilterQuery(database));
    }

    CalendarioService(Clock clock) {
        this(clock, (from, to, operatoreId) -> List.of(), new EmptyClientiFilterQuery());
    }

    CalendarioService(
            Clock clock, CalendarioQuery calendarioQuery, ClientiFilterQuery clientiFilterQuery) {
        this.clock = clock;
        this.calendarioQuery = calendarioQuery;
        this.clientiFilterQuery = clientiFilterQuery;
    }

    public YearMonth currentMonth() {
        return YearMonth.now(clock);
    }

    public LocalDate currentDate() {
        return LocalDate.now(clock);
    }

    public CalendarioMonth getMonth(YearMonth month, UUID operatoreId) {
        LocalDate today = LocalDate.now(clock);
        Integer todayDay = month.equals(YearMonth.from(today)) ? today.getDayOfMonth() : null;
        String monthName = month.getMonth().getDisplayName(TextStyle.FULL, ITALIAN);
        String periodLabel =
                monthName.substring(0, 1).toUpperCase(ITALIAN)
                        + monthName.substring(1)
                        + " "
                        + month.getYear();

        Map<LocalDate, List<CalendarioCall>> callsByDate =
                getCallsByDate(month.atDay(1), month.atEndOfMonth(), operatoreId);

        return new CalendarioMonth(
                periodLabel,
                month.atDay(1).getDayOfWeek().getValue() - 1,
                month.lengthOfMonth(),
                todayDay,
                callsByDate);
    }

    public CalendarioMonth getMonth(YearMonth month) {
        return getMonth(month, null);
    }

    public CalendarioWeek getWeek(LocalDate referenceDate, UUID operatoreId) {
        LocalDate startDate =
                referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endDate = startDate.plusDays(6);
        LocalDate today = LocalDate.now(clock);
        return new CalendarioWeek(
                weekPeriodLabel(startDate, endDate),
                startDate,
                endDate,
                today.isBefore(startDate) || today.isAfter(endDate) ? null : today,
                getCallsByDate(startDate, endDate, operatoreId));
    }

    public List<OperatoreFilter> getOperatorFilters() {
        return clientiFilterQuery.findOperatoriConClienti().stream()
                .map(
                        operator ->
                                new OperatoreFilter(
                                        operator.id(),
                                        operatorLabel(
                                                operator.nome(),
                                                operator.cognome(),
                                                operator.username())))
                .toList();
    }

    private String operatorLabel(String nome, String cognome, String username) {
        String fullName =
                ((nome == null ? "" : nome.trim()) + " " + (cognome == null ? "" : cognome.trim()))
                        .trim();
        return fullName.isBlank() ? username : fullName;
    }

    private Map<LocalDate, List<CalendarioCall>> getCallsByDate(
            LocalDate from, LocalDate to, UUID operatoreId) {
        return calendarioQuery.findProssimeChiamate(from, to, operatoreId).stream()
                .map(
                        call ->
                                new CalendarioCall(
                                        call.interazioneId(),
                                        call.clienteId(),
                                        call.ragioneSociale(),
                                        call.operatoreId(),
                                        call.operatore(),
                                        call.prossimoContatto()))
                .collect(Collectors.groupingBy(CalendarioCall::data));
    }

    private String weekPeriodLabel(LocalDate startDate, LocalDate endDate) {
        String startMonth = startDate.getMonth().getDisplayName(TextStyle.FULL, ITALIAN);
        String endMonth = endDate.getMonth().getDisplayName(TextStyle.FULL, ITALIAN);
        if (startDate.getYear() != endDate.getYear()) {
            return startDate.getDayOfMonth()
                    + " "
                    + startMonth
                    + " "
                    + startDate.getYear()
                    + " - "
                    + endDate.getDayOfMonth()
                    + " "
                    + endMonth
                    + " "
                    + endDate.getYear();
        }
        if (startDate.getMonth() != endDate.getMonth()) {
            return startDate.getDayOfMonth()
                    + " "
                    + startMonth
                    + " - "
                    + endDate.getDayOfMonth()
                    + " "
                    + endMonth
                    + " "
                    + endDate.getYear();
        }
        return startDate.getDayOfMonth()
                + " - "
                + endDate.getDayOfMonth()
                + " "
                + endMonth
                + " "
                + endDate.getYear();
    }

    private static final class EmptyClientiFilterQuery implements ClientiFilterQuery {
        @Override
        public List<OperatoreClienteFilterResult> findOperatoriConClienti() {
            return List.of();
        }

        @Override
        public List<String> findTipiCliente() {
            return List.of();
        }

        @Override
        public List<String> findStatiTrattativa() {
            return List.of();
        }
    }
}
