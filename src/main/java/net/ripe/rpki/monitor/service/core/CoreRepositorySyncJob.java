package net.ripe.rpki.monitor.service.core;

import lombok.AllArgsConstructor;
import net.ripe.rpki.monitor.repositories.RepositoriesState;
import net.ripe.rpki.monitor.repositories.RepositoryEntry;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

@Component
@AllArgsConstructor(onConstructor_ = {@Autowired})
@ConditionalOnProperty(value = "core.included", havingValue = "true")
public class CoreRepositorySyncJob extends QuartzJobBean {
    private final RepositoriesState state;
    private final CoreClient coreClient;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        var content = coreClient.publishedObjects();
        state.updateByTag(
                coreClient.getName(),
                Instant.now(),
                content.stream().map(RepositoryEntry::from)
        );
    }

    @Bean("CoreRepositorySyncJob")
    public JobDetail job() {
        return JobBuilder.newJob().ofType(getClass())
                .storeDurably()
                .withIdentity("Core_Repository_Sync")
                .withDescription("Sync RPKI-core repository")
                .build();
    }

    @Bean("CoreRepositorySyncTrigger")
    public Trigger trigger(@Value("${core.interval}") Duration interval,
                           @Value("${core.initial-delay}") Duration initialDelay,
                           @Qualifier("CoreRepositorySyncJob") JobDetail job) {
        var start = Instant.now().plus(initialDelay);

        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity(job.getKey().getName() + "_Trigger")
                .withDescription(job.getDescription())
                .withSchedule(simpleSchedule().repeatForever().withIntervalInSeconds((int)interval.toSeconds()))
                .startAt(Date.from(start))
                .build();
    }

}
