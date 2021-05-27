package net.ripe.rpki.monitor.publishing;

import lombok.extern.slf4j.Slf4j;
import net.ripe.rpki.monitor.metrics.CollectorUpdateMetrics;
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
import java.util.Set;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

@Slf4j
@Component
public class PublishedObjectsSummaryJob extends QuartzJobBean {
    private final static String PUBLISHED_OBJECTS_JOB = "Published_Objects_Job_Detail";

    @Autowired
    private PublishedObjectsSummaryService publishedObjectsSummaryService;

    @Autowired
    private CollectorUpdateMetrics collectorUpdateMetrics;

    @Bean(PUBLISHED_OBJECTS_JOB)
    public JobDetail jobDetail() {
        return JobBuilder.newJob().ofType(PublishedObjectsSummaryJob.class)
                .storeDurably()
                .withIdentity(PUBLISHED_OBJECTS_JOB)
                .withDescription("Invoke Rrdp Expiration Job service...")
                .build();
    }

    @Bean("Published_Objects_Summary_Trigger")
    public Trigger trigger(@Qualifier(PUBLISHED_OBJECTS_JOB) JobDetail jobDetail,
                           @Value("${published-objects.full-diff-interval}") Duration interval
                           ) {
        return TriggerBuilder.newTrigger().forJob(jobDetail)
                .withIdentity(this.getClass().getName())
                .withDescription("Published object comparison trigger")
                .withSchedule(simpleSchedule().repeatForever().withIntervalInSeconds((int)interval.toSeconds()))
                .startAt(DateTime.now().plusMinutes(3).toDate()) // After initial rsync run was started.
                .build();
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            final var diff = publishedObjectsSummaryService.getPublishedObjectsDiff();
            final var total = diff.values().stream().mapToInt(Set::size).sum();
            collectorUpdateMetrics.trackSuccess(getClass().getSimpleName(), "total").objectCount(total, 0, 0);
        } catch (Exception e) {
            collectorUpdateMetrics.trackFailure(getClass().getSimpleName(), "total").zeroCounters();
            throw e;
        }
    }
}
