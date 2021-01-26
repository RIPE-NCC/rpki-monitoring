package net.ripe.rpki.monitor.expiration;

import net.ripe.rpki.monitor.expiration.fetchers.FetcherException;
import org.joda.time.DateTime;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

@Component
public class RsyncObjectsAboutToExpireCollectorJob extends QuartzJobBean {
    private final List<ObjectAndDateCollector> collectors;

    @Autowired
    public RsyncObjectsAboutToExpireCollectorJob(final Collectors collectors) {
        this.collectors = collectors.getRsyncCollectors();
    }

    @Bean("Rsync_Expiration_Job_Detail")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(RsyncObjectsAboutToExpireCollectorJob.class)
            .storeDurably()
            .withIdentity("Rsync_Expiration_Job_Detail")
            .withDescription("Invoke Rsync Expiration Job service...")
            .build();
    }

    @Bean("Rsync_Expiration_Trigger")
    public Trigger trigger(
        @Qualifier("Rsync_Expiration_Job_Detail") JobDetail job,
        @Value("${rsync.interval}") Duration interval
    ) {
        return TriggerBuilder.newTrigger().forJob(job)
            .withIdentity("Rsync_Expiration_Trigger")
            .withDescription("Rsync Expiration trigger")
            .withSchedule(simpleSchedule().repeatForever().withIntervalInSeconds((int) interval.toSeconds()))
            .startAt(DateTime.now().plusMinutes(1).toDate())
            .build();
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            collectors.parallelStream().forEach(ObjectAndDateCollector::run);
        } catch (FetcherException e) {
            throw new JobExecutionException(e);
        }
    }
}