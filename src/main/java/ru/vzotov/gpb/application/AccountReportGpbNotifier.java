package ru.vzotov.gpb.application;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.vzotov.accounting.application.AccountReportService;

@Component
public class AccountReportGpbNotifier {

    private static final long TEN_MINUTES = 10 * 60 * 1000;

    @Autowired
    @Qualifier("AccountReportServiceGpb")
    private AccountReportService accountReportService;

    @Scheduled(initialDelay = 30 * 1000, fixedDelay = TEN_MINUTES)
    public void searchNewReports() {
        accountReportService.processNewReports();
    }
}
