package net.ripe.rpki.monitor.expiration;

import com.google.common.collect.ComparisonChain;
import com.google.common.hash.HashCode;
import lombok.Value;
import net.ripe.rpki.monitor.HasHashAndUri;

import java.time.Instant;
import java.util.Comparator;
import java.util.Date;

@Value
public class RepoObject implements Comparable<RepoObject>, HasHashAndUri {
    private static Comparator<RepoObject> COMPARE_REPO_OBJECT = Comparator.comparing(RepoObject::getExpiration)
            .thenComparing(RepoObject::getCreation)
            .thenComparing(RepoObject::getUri)
            .thenComparing(RepoObject::getSha256);

    Date creation;
    Date expiration;
    String uri;
    byte[] sha256;

    @Override
    public int compareTo(RepoObject o) {
        return COMPARE_REPO_OBJECT.compare(this, o);
    }

    public static RepoObject fictionalObjectValidAtInstant(final Instant then) {
        var asDate = Date.from(then);
        return new RepoObject(asDate, asDate, "NA", new byte[]{0});
    }

    public String getSha256() {
        return HashCode.fromBytes(sha256).toString();
    }
}
