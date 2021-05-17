package net.ripe.rpki.monitor.expiration;

import com.google.common.collect.ComparisonChain;
import com.google.common.hash.HashCode;
import lombok.Value;
import net.ripe.rpki.monitor.HasHashAndUri;

import java.util.Date;

@Value
public class RepoObject implements Comparable<RepoObject>, HasHashAndUri {
    Date creation;
    Date expiration;
    String uri;
    byte[] sha256;

    @Override
    public int compareTo(RepoObject o) {
        return ComparisonChain.start()
            .compare(this.expiration, o.expiration)
            .compare(this.creation, o.creation)
            .compare(this.getUri(), o.getUri())
            .compare(this.getSha256(), o.getSha256())
            .result();
    }

    public static RepoObject fictionalObjectValidAtInstant(final Date date) {
        return new RepoObject(date, date, "NA", new byte[]{0});
    }

    public String getSha256() {
        return HashCode.fromBytes(sha256).toString();
    }
}
