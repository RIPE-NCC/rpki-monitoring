package net.ripe.rpki.monitor.expiration;

import org.joda.time.DateTime;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.Duration;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

@Component
public class RsyncObjectsAboutToExpireCollectorJob extends ObjectsAboutToExpireCollectorJob {

    @Autowired
    public RsyncObjectsAboutToExpireCollectorJob(Collectors collectors) {
        super(collectors.getRsyncCollectors(), collectors.getThreadLimiter(), collectors.getRegistry());
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
        @Value("${rsync.interval:10m}") Duration interval) {
        return
            TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("Rsync_Expiration_Trigger")
                .withDescription("Rsync Expiration trigger")
                .withSchedule(simpleSchedule().repeatForever().withIntervalInSeconds((int) interval.toSeconds()))
                .startAt(DateTime.now().plusMinutes(1).toDate())
                .build();
    }
}
