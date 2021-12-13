package com.zf.home.gpb.infrastructure.fs;

import com.zf.home.gpb.domain.model.GpbOperation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vzotov.accounting.domain.model.AccountReport;
import ru.vzotov.accounting.domain.model.AccountReportId;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnit4.class)
public class AccountReportRepositoryFilesTest {

    private static final Logger log = LoggerFactory.getLogger(AccountReportRepositoryFilesTest.class);

    @Test
    public void find() {
        File resourcesDirectory = new File("src/test/resources/account-reports");
        GpbReportRepositoryFiles repo = new GpbReportRepositoryFiles(resourcesDirectory.getAbsolutePath());
        List<AccountReportId> all = repo.findAll();
        List<AccountReport<GpbOperation>> reports = new ArrayList<>();
        for (AccountReportId id : all) {
            reports.add(repo.find(id));
        }
        assertThat(reports).hasSize(2);


        AccountReport<GpbOperation> report = reports.stream()
                .filter(r -> "report_2.csv".equalsIgnoreCase(r.reportId().name()))
                .findAny()
                .orElse(null);
        assertThat(report).isNotNull();
        List<GpbOperation> operations = report.operations();
        operations.forEach(op -> {
            log.info("Operation {}", op);
            assertThat(op.description().length()).isLessThan(512);
        });
        assertThat(operations).hasSize(16);

        GpbOperation operation = operations.get(0);
        assertThat(operation.operationDate()).isEqualTo(LocalDateTime.of(2021, 3, 7, 15, 20, 40));
        assertThat(operation.operationAmount()).isEqualTo(4657.48d);

        report = reports.stream()
                .filter(r -> "report_1.csv".equalsIgnoreCase(r.reportId().name()))
                .findAny()
                .orElse(null);
        assertThat(report).isNotNull();
        operations = report.operations();
        operations.forEach(op -> {
            log.info("Operation {}", op);
            assertThat(op.description().length()).isLessThan(512);
        });
        assertThat(operations).hasSize(18);
    }

}
