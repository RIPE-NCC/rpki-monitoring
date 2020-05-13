package net.ripe.rpki.monitor.expiration;

import java.util.Date;

public class RepoObject implements Comparable<RepoObject> {
    final private Date expiration;
    final private String uri;

    public RepoObject(final Date expiration, final String uri) {
        this.expiration = expiration;
        this.uri = uri;
    }

    public String getExpiration() {
        return expiration.toString();
    }

    @Override
    public int compareTo(RepoObject o) {

        final int i = this.expiration.compareTo(o.expiration);

        if(i == 0) {
           return this.getUri().compareTo(o.getUri());
        }

        return i;
    }

    public static final RepoObject fictionalObjectExpiringOn(final Date date) {
        return new RepoObject(date, "NA");
    }

    public String getUri() {
        return uri;
    }
}
