package net.ripe.rpki.monitor.certificateanalysis;

import lombok.experimental.UtilityClass;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResource;

@UtilityClass
public class CertificateAnalysisTestValues {
    public final static ImmutableResourceSet TEST_NET_1 = ImmutableResourceSet.parse("192.0.2.0/24");
    public final static ImmutableResourceSet TEST_NET_2 = ImmutableResourceSet.parse("198.51.100.0/24");

    public static final String APNIC_TRUST_ANCHOR_CERTIFICATE_URL = "rsync://rpki.apnic.net/repository/apnic-rpki-root-iana-origin.cer";
    public static final String RIPE_TRUST_ANCHOR_CERTIFICATE_URL = "rsync://rpki.ripe.net/ta/ripe-ncc-ta.cer";
    public static final ImmutableResourceSet ALL_IPv4_RESOURCE_SET = ImmutableResourceSet.of(IpResource.ALL_IPV4_RESOURCES);
}
