package net.ripe.rpki.monitor.expiration;

import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

@Service
public class SummaryService {

    private ConcurrentSkipListSet<RepoObject> rrdpSummary = new ConcurrentSkipListSet();
    private ConcurrentSkipListSet<RepoObject> rsyncSummary = new ConcurrentSkipListSet();

    public Set<RepoObject> getRrdpObjectsAboutToExpire(final int inHours) {
        final RepoObject upTo = RepoObject.fictionalObjectExpiringOn(DateTime.now().plusHours(inHours).toDate());
        return rrdpSummary.headSet(upTo);
    }

    public Set<RepoObject> getRsyncObjectsAboutToExpire(final int inHours) {
        final RepoObject upTo = RepoObject.fictionalObjectExpiringOn(DateTime.now().plusHours(inHours).toDate());
        return rsyncSummary.headSet(upTo);
    }

    public synchronized void setRrdpSummary(final ConcurrentSkipListSet<RepoObject> dateSummary) {
        rrdpSummary = dateSummary;
    }

    public synchronized void setRsyncSummary(final ConcurrentSkipListSet<RepoObject> dateSummary) {
        rsyncSummary = dateSummary;
    }

}
