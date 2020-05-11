package net.ripe.rpki.monitor.expiration;

import com.google.common.hash.HashCode;
import net.ripe.rpki.monitor.HasHashAndUri;

import java.util.Date;

public class RepoObject implements Comparable<RepoObject>, HasHashAndUri {
    final private Date expiration;
    final private byte[] sha256;
    final private String uri;

    public RepoObject(final Date expiration, final String uri, final byte[] sha256) {
        this.expiration = expiration;
        this.uri = uri;
        this.sha256 = sha256;
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
        return new RepoObject(date, "NA", new byte[]{});
    }

    public String getUri() {
        return uri;
    }

    public String getSha256() {
        return HashCode.fromBytes(sha256).toString();
    }
}
