package net.ripe.rpki.monitor.expiration;

import com.google.common.base.Preconditions;
import com.google.common.hash.HashCode;
import com.google.common.primitives.UnsignedBytes;
import lombok.Getter;
import net.ripe.rpki.monitor.HasHashAndUri;

import java.time.Instant;
import java.util.Comparator;
import java.util.Date;

public record RepoObject(Instant creation, Instant expiration, @Getter String uri, byte[] sha256) implements Comparable<RepoObject>, HasHashAndUri {
    private static final Comparator<RepoObject> COMPARE_REPO_OBJECT = Comparator.comparing(RepoObject::expiration)
            .thenComparing(RepoObject::creation)
            .thenComparing(RepoObject::uri)
            .thenComparing(Comparator.comparing(RepoObject::sha256, UnsignedBytes.lexicographicalComparator()));

    public RepoObject {
        Preconditions.checkArgument(sha256.length == 32, "sha256 hashes are 256b/8 bytes long");
    }
    @Override
    public int compareTo(RepoObject o) {
        return COMPARE_REPO_OBJECT.compare(this, o);
    }

    public static RepoObject fictionalObjectValidAtInstant(final Instant then) {
        return new RepoObject(then, then, "NA", new byte[32]);
    }
}
