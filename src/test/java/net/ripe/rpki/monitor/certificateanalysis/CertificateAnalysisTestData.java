package net.ripe.rpki.monitor.certificateanalysis;

import lombok.experimental.UtilityClass;
import net.ripe.ipresource.ImmutableResourceSet;

@UtilityClass
public class CertificateAnalysisTestData {
    public final static ImmutableResourceSet TEST_NET_1 = ImmutableResourceSet.parse("192.0.2.0/24");
    public final static ImmutableResourceSet TEST_NET_2 = ImmutableResourceSet.parse("198.51.100.0/24");
    public final static ImmutableResourceSet TEST_NET_3 = ImmutableResourceSet.parse("203.0.113.0/24");
}
