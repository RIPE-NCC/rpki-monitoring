package net.ripe.rpki.monitor.certificateanalysis;

import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

public class PrefixTreeTest {
    public static IpRange IPV6_DOCUMENTATION_RANGE = IpRange.parse("2001:DB8::/32");
    public static IpRange TEST_NET_1 = IpRange.parse("192.0.2.0/24");
    public static IpRange TEST_NET_2 = IpRange.parse("198.51.100.0/24");

    public static IpRange TEST_NET_3 = IpRange.parse("203.0.113.0/24");

    public static IpRange IPv4_SINGLE_ADDR = IpRange.parse("203.0.113.255/32");
    public static IpRange IPv6_SINGLE_ADDR = IpRange.parse("2001:DB8::/128");

    private PrefixTree<String> subjectV4;
    private PrefixTree<String> subjectV6;

    @BeforeEach
    public void setUp() {
        subjectV4 = new PrefixTree<>(IpResourceType.IPv4);
        subjectV6 = new PrefixTree<>(IpResourceType.IPv6);
    }

    @Test
    public void testGetKey_aligned() {
        assertThat(PrefixTree.getKey(IpRange.ALL_IPV4_RESOURCES))
                .hasSize(0);

        assertThat(PrefixTree.getKey(IpRange.ALL_IPV6_RESOURCES))
                .hasSize(0);

        assertThat(PrefixTree.getKey(IpRange.ALL_IPV6_RESOURCES))
                .hasSize(0);

        assertThat(PrefixTree.getKey(IpRange.parse("192.0.2.128/25")))
                .isEqualTo("11000000" + "00000000" + "00000010" + "1")
                .hasSize(25);

        assertThat(PrefixTree.getKey(IpRange.parse("192.0.2.128/26")))
                .isEqualTo("11000000" + "00000000" + "00000010" + "10")
                .hasSize(26);

        // IP address representations below are from ipcalc
        assertThat(PrefixTree.getKey(IPV6_DOCUMENTATION_RANGE))
                .isEqualTo("0010000000000001" + "0000110110111000")
                .hasSize(32);

        assertThat(PrefixTree.getKey(IPv4_SINGLE_ADDR))
                .isEqualTo("11001011" + "00000000" + "01110001" + "11111111")
                .hasSize(32);

        assertThat(PrefixTree.getKey(IPv6_SINGLE_ADDR))
                .isEqualTo("0010000000000001" + "0000110110111000" + "0000000000000000".repeat(6))
                .hasSize(128);
    }

    @Test
    public void testGetPrefixFromKey() {
        assertThat(subjectV4.rangeFromKey(PrefixTree.getKey(TEST_NET_1)))
                .isEqualTo(TEST_NET_1);

        assertThat(subjectV6.rangeFromKey(PrefixTree.getKey(IPV6_DOCUMENTATION_RANGE)))
                .isEqualTo(IPV6_DOCUMENTATION_RANGE);

        assertThat(subjectV4.rangeFromKey(PrefixTree.getKey(IPv4_SINGLE_ADDR)))
                .isEqualTo(IPv4_SINGLE_ADDR);

        assertThat(subjectV6.rangeFromKey(PrefixTree.getKey(IPv6_SINGLE_ADDR)))
                .isEqualTo(IPv6_SINGLE_ADDR);
    }
}

