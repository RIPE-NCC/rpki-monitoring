package net.ripe.rpki.monitor.publishing;

import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Component
public class PublishedObjectsSummaryJob extends QuartzJobBean {
    private final static String PUBLISHED_OBJECTS_JOB = "Published_Objects_Job_Detail";
    @Autowired
    private PublishedObjectsSummary publishedObjectsSummary;

    @Bean(PUBLISHED_OBJECTS_JOB)
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(PublishedObjectsSummaryJob.class)
                .storeDurably()
                .withIdentity(PUBLISHED_OBJECTS_JOB)
                .withDescription("Invoke Rrdp Expiration Job service...")
                .build();
    }

    @Bean("Published_Objects_Summary_Trigger")
    public Trigger trigger(@Qualifier(PUBLISHED_OBJECTS_JOB) JobDetail jobDetail) {
        return TriggerBuilder.newTrigger().forJob(jobDetail)
                .withIdentity(this.getClass().getName())
                .withDescription("Published object comparison trigger")
                .withSchedule(simpleSchedule().repeatForever().withIntervalInMinutes(5))
                .startAt(DateTime.now().plusMinutes(6).toDate())
                .build();
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        publishedObjectsSummary.getPublishedObjectsDiff();
    }
}
