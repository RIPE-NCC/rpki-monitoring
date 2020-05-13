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
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

@Component
public class RrdpObjectsAboutToExpireCollectorJob extends QuartzJobBean {


    private final RrdpObjectsAboutToExpireCollector collector;

    @Autowired
    public RrdpObjectsAboutToExpireCollectorJob(final RrdpObjectsAboutToExpireCollector collector) {
        this.collector = collector;
    }

    @Bean("Rrdp_Expiration_Job_Detail")
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(RrdpObjectsAboutToExpireCollectorJob.class)
                .storeDurably()
                .withIdentity("Rrdp_Expiration_Job_Detail")
                .withDescription("Invoke Rrdp Expiration Job service...")
                .build();
    }

    @Bean("Rrdp_Expiration_Trigger")
    public Trigger trigger(@Qualifier("Rrdp_Expiration_Job_Detail") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("Rrdp_Expiration_Trigger")
                .withDescription("Rrdp Expiration trigger")
                .withSchedule(simpleSchedule().repeatForever().withIntervalInMinutes(20))
                .startAt(DateTime.now().plusMinutes(3).toDate())
                .build();
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            collector.run();
        } catch (FetcherException e) {
            throw new JobExecutionException(e);
        }
    }
}
