package ru.vzotov.gpb.application.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.vzotov.accounting.application.AccountNotFoundException;
import ru.vzotov.accounting.application.AccountReportNotFoundException;
import ru.vzotov.accounting.application.AccountingService;
import ru.vzotov.accounting.domain.model.AccountReport;
import ru.vzotov.accounting.domain.model.AccountReportId;
import ru.vzotov.accounting.domain.model.AccountReportRepository;
import ru.vzotov.accounting.domain.model.AccountRepository;
import ru.vzotov.accounting.domain.model.CardRepository;
import ru.vzotov.banking.domain.model.Account;
import ru.vzotov.banking.domain.model.AccountNumber;
import ru.vzotov.banking.domain.model.BankId;
import ru.vzotov.banking.domain.model.OperationId;
import ru.vzotov.banking.domain.model.OperationType;
import ru.vzotov.banking.domain.model.TransactionReference;
import ru.vzotov.domain.model.Money;
import ru.vzotov.gpb.GazprombankConfig;
import ru.vzotov.gpb.domain.model.GpbOperation;
import ru.vzotov.person.domain.model.PersonId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;

public class AccountReportServiceImplTest {

    private static final AccountNumber ACCOUNT_NUMBER = new AccountNumber("40817810518370123456");
    private AccountReportServiceGpb service;
    private AccountReportId reportId;
    private AccountReportRepository<GpbOperation> reportRepository;
    private AccountingService accountingService;
    private AccountRepository accountRepository;
    private CardRepository cardRepository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        reportRepository = (AccountReportRepository<GpbOperation>) Mockito.mock(AccountReportRepository.class);
        accountingService = Mockito.mock(AccountingService.class);
        accountRepository = Mockito.mock(AccountRepository.class);
        cardRepository = Mockito.mock(CardRepository.class);

        service = new AccountReportServiceGpb(reportRepository, accountingService, accountRepository, cardRepository,
                new GazprombankConfig());
        reportId = new AccountReportId("test-1", LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC));

        List<GpbOperation> operations = Arrays.asList(
                new GpbOperation(

                        LocalDateTime.of(2020, Month.FEBRUARY, 21, 20, 0, 31),
                        ACCOUNT_NUMBER.number(),
                        null,
                        2000d,
                        "RUR",
                        "Перевод на счет",
                        false
                ),
                new GpbOperation(

                        LocalDateTime.of(2020, Month.MARCH, 9, 16, 26, 49),
                        ACCOUNT_NUMBER.number(),
                        null,
                        -809d,
                        "RUR",
                        "Перевод на счет 2",
                        false
                )
        );

        Mockito.when(reportRepository.find(reportId))
                .thenReturn(new AccountReport<>(reportId, operations));

        Mockito.when(accountingService.registerOperation(
                Mockito.any(AccountNumber.class),
                Mockito.any(LocalDate.class),
                Mockito.any(TransactionReference.class),
                Mockito.any(OperationType.class),
                Mockito.any(Money.class),
                Mockito.anyString()
        )).thenReturn(new OperationId("test-op-1"), new OperationId("test-op-2"));

        Mockito.when(accountRepository.find(ACCOUNT_NUMBER))
                .thenReturn(new Account(ACCOUNT_NUMBER,
                        "ГПБ счет", BankId.GAZPROMBANK, Currency.getInstance("RUR"), new PersonId("vzotov")));
    }

    @Test
    public void processAccountReport() throws AccountReportNotFoundException, AccountNotFoundException {
        service.processAccountReport(new AccountReportId("test-1", LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC)));
        Mockito.verify(accountingService).registerOperation(
                ACCOUNT_NUMBER,
                LocalDate.of(2020, Month.FEBRUARY, 21),
                new TransactionReference("e5659291fcc901c7dc5b051505bf35aa"),
                OperationType.DEPOSIT,
                Money.kopecks(200000),
                "Перевод на счет"
        );
        Mockito.verify(accountingService).registerOperation(
                ACCOUNT_NUMBER,
                LocalDate.of(2020, Month.MARCH, 9),
                new TransactionReference("384c0abfb32772918e5015a96e5e373a"),
                OperationType.WITHDRAW,
                Money.rubles(809d),
                "Перевод на счет 2"
        );
    }

}
