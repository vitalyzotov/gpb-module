package ru.vzotov.gpb.application.impl;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import ru.vzotov.accounting.application.AccountNotFoundException;
import ru.vzotov.accounting.application.AccountReportNotFoundException;
import ru.vzotov.accounting.application.AccountReportService;
import ru.vzotov.accounting.application.AccountingService;
import ru.vzotov.accounting.domain.model.AccountReport;
import ru.vzotov.accounting.domain.model.AccountReportId;
import ru.vzotov.accounting.domain.model.AccountReportRepository;
import ru.vzotov.accounting.domain.model.AccountRepository;
import ru.vzotov.accounting.domain.model.CardRepository;
import ru.vzotov.banking.domain.model.Account;
import ru.vzotov.banking.domain.model.AccountNumber;
import ru.vzotov.banking.domain.model.BankId;
import ru.vzotov.banking.domain.model.Card;
import ru.vzotov.banking.domain.model.OperationId;
import ru.vzotov.banking.domain.model.OperationType;
import ru.vzotov.banking.domain.model.TransactionReference;
import ru.vzotov.domain.model.Money;
import ru.vzotov.gpb.GazprombankConfig;
import ru.vzotov.gpb.domain.model.GpbOperation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ru.vzotov.banking.domain.model.OperationType.DEPOSIT;
import static ru.vzotov.banking.domain.model.OperationType.WITHDRAW;

@Service
@Qualifier("AccountReportServiceGpb")
public class AccountReportServiceGpb implements AccountReportService {

    private static final Logger log = LoggerFactory.getLogger(AccountReportServiceGpb.class);

    private final AccountReportRepository<GpbOperation> accountReportRepository;

    private final AccountingService accountingService;

    private final AccountRepository accountRepository;

    private final CardRepository cardRepository;

    private final GazprombankConfig gazprombankConfig;

    AccountReportServiceGpb(
            @Autowired @Qualifier("accountReportRepositoryGpb") AccountReportRepository<GpbOperation> accountReportRepository
            , @Autowired AccountingService accountingService
            , @Autowired AccountRepository accountRepository
            , @Autowired CardRepository cardRepository
            , @Autowired GazprombankConfig gazprombankConfig
    ) {
        this.accountReportRepository = accountReportRepository;
        this.accountingService = accountingService;
        this.accountRepository = accountRepository;
        this.cardRepository = cardRepository;
        this.gazprombankConfig = gazprombankConfig;
    }

    public List<String> getSkipIds() {
        return Collections.unmodifiableList(gazprombankConfig.getSkip());
    }

    @Override
    public BankId bankId() {
        return BankId.GAZPROMBANK;
    }

    @Override
    public AccountReportId save(String name, InputStream content) throws IOException {
        Validate.notNull(name);
        Validate.notNull(content);
        return accountReportRepository.save(name, content);
    }

    @Override
    public void processAccountReport(AccountReportId reportId) throws AccountReportNotFoundException, AccountNotFoundException {
        Validate.notNull(reportId);

        final AccountReport<GpbOperation> report = accountReportRepository.find(reportId);
        if (report == null) {
            throw new AccountReportNotFoundException();
        }

        final Set<String> skipAccounts = new HashSet<>(gazprombankConfig.getSkip() == null ? Collections.emptyList() : gazprombankConfig.getSkip());
        log.info("Process report {} skipping accounts {}", reportId, skipAccounts);

        // Index of cards by their card number in report
        final Map<String, Card> cards = new HashMap<>();
        final Map<String, Account> accounts = new HashMap<>();

        for (GpbOperation row : report.operations()) {
            final OperationType type = row.operationAmount() < 0d ? WITHDRAW : DEPOSIT;

            Card card = null;
            if (row.cardNumber() != null && !row.cardNumber().isEmpty()) {
                card = cards.get(row.cardNumber());
                if (card == null) {
                    List<Card> cardList = cardRepository.findByMask(row.cardNumber())
                            .stream()
                            .filter(c -> BankId.GAZPROMBANK.equals(c.issuer()))
                            .toList();

                    if (cardList.isEmpty()) {
                        log.error("Unable to find card by mask {}", row.cardNumber());
                        return;
                    } else if (cardList.size() == 1) {
                        card = cardList.get(0);
                        cards.put(row.cardNumber(), card);
                    } else {
                        log.error("Multiple cards found by mask {}", row.cardNumber());
                        return;
                    }
                }
            }

            final Currency currency = Currency.getInstance(row.operationCurrency());
            final Money amount = new Money(Math.abs(row.operationAmount()), currency);

            if (skipAccounts.contains(row.accountNumber())) {
                log.warn("Skip operation {} for accountId {}", amount, row.accountNumber());
                continue;
            }

            final Account account;
            if (card == null) {
                account = accounts.computeIfAbsent(row.accountNumber(),
                        number -> accountRepository.find(new AccountNumber(row.accountNumber())));
                if (!(account != null && BankId.GAZPROMBANK.equals(account.bankId()) &&
                        (account.currency() == null || currency.equals(account.currency())))) {
                    log.error("Unable to find account for gazprombank, alias {}, currency {}", row.accountNumber(), currency);
                    return;
                }
            } else {
                account = accountRepository.findAccountOfCard(card.cardNumber(), row.operationDate().toLocalDate());
                if (account == null) {
                    log.error("Unable to find account for card {} and date {}", card.cardNumber(), row.operationDate());
                    return;
                }
            }

            final AccountNumber accountNumber = account.accountNumber();

            // Пропускаем записи о блокировании средств на счете.
            // Это незавершенные операции, нельзя их учитывать как полноценные операции
            // Для Газпромбанка работать не будет, т.к. у них проблемы в API по учету заблокированных средств
            if (row.hold()) {
                accountingService.registerHoldOperation(
                        accountNumber,
                        row.operationDate().toLocalDate(),
                        type,
                        amount,
                        row.description()
                );
            } else {
                final String transactionId = DigestUtils.md5DigestAsHex(
                        (row.operationDate().toString() + "_" + accountNumber.number() + "_" + row.operationAmount().toString() + "_" + row.description())
                                .getBytes(StandardCharsets.UTF_8)
                );

                OperationId operationId = accountingService.registerOperation(
                        accountNumber,
                        row.operationDate().toLocalDate(),
                        new TransactionReference(transactionId),
                        type,
                        amount,
                        row.description()
                );

                accountingService.removeMatchingHoldOperations(operationId);
            }
        }

        accountReportRepository.markProcessed(reportId);
    }

    @Override
    public void processNewReports() {
        List<AccountReportId> reports = accountReportRepository.findUnprocessed();

        log.info("Found {} unprocessed reports", reports.size());

        for (AccountReportId reportId : reports) {
            log.info("Start processing of report {}", reportId);
            try {
                processAccountReport(reportId);

                log.info("Processing of report {} finished", reportId);
            } catch (AccountReportNotFoundException | AccountNotFoundException e) {
                log.warn("Processing failed for report {}", reportId);
            }
        }
    }
}
