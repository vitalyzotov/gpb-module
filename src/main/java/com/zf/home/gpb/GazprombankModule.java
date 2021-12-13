package com.zf.home.gpb;

import com.zf.home.gpb.domain.model.GpbOperation;
import com.zf.home.gpb.infrastructure.fs.GpbReportRepositoryFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.vzotov.accounting.domain.model.AccountReportRepository;

@Configuration
public class GazprombankModule {

    private static final Logger log = LoggerFactory.getLogger(GazprombankModule.class);

    @Bean
    @ConfigurationProperties("gazprombank.reports")
    public GazprombankConfig gazprombankConfig() {
        return new GazprombankConfig();
    }

    @Bean
    public AccountReportRepository<GpbOperation> accountReportRepositoryGpb(GazprombankConfig config) {
        log.info("Create gazprombank report repository for path {}", config.getPath());
        return new GpbReportRepositoryFiles(config.getPath());
    }


}
