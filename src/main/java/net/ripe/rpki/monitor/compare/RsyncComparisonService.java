package net.ripe.rpki.monitor.compare;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import net.ripe.rpki.monitor.publishing.dto.FileEntry;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;


/**
 * Fetches two rsync repositories, compares them and reports the result.
 */
@Slf4j
@Service
public class RsyncComparisonService {

    private final MeterRegistry registry;
    private final String rsyncPremise;
    private final String rsyncAws;

    @Autowired
    public RsyncComparisonService(MeterRegistry registry,
                                  @Value("${rsync.url}") String rsyncPremise,
                                  @Value("${rsync.aws-url}") String rsyncAws) {
        this.registry = registry;
        this.rsyncPremise = rsyncPremise;
        this.rsyncAws = rsyncAws;
    }


    void updateRsyncComparisonMetrics() {
        final RsyncComparison comparison = new RsyncComparison(rsyncPremise, rsyncAws);
        Optional<Pair<Map<String, byte[]>, Map<String, byte[]>>> maps = comparison.fetchBoth();
        if (maps.isEmpty()) {
            log.info("Could not fetch at least one of the repositories, bailing out");
        } else {
            Map<String, byte[]> m1 = maps.get().getLeft();
            Map<String, byte[]> m2 = maps.get().getRight();



        }
    }
}
