package net.ripe.rpki.monitor.certificateanalysis;

import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    // key -> prefix and inverse

    @Test
    void testGetKey_aligned() {
        assertThat(PrefixTree.getKey(IpRange.ALL_IPV4_RESOURCES))
                .hasSize(1);

        assertThat(PrefixTree.getKey(IpRange.ALL_IPV6_RESOURCES))
                .hasSize(1);

        assertThat(PrefixTree.getKey(IpRange.ALL_IPV6_RESOURCES))
                .hasSize(1);

        assertThat(PrefixTree.getKey(IpRange.parse("192.0.2.128/25")))
                .isEqualTo(PrefixTree.KEY_MARKER + "11000000" + "00000000" + "00000010" + "1")
                .hasSize(26);

        assertThat(PrefixTree.getKey(IpRange.parse("192.0.2.128/26")))
                .isEqualTo(PrefixTree.KEY_MARKER + "11000000" + "00000000" + "00000010" + "10")
                .hasSize(27);

        // IP address representations below are from ipcalc
        assertThat(PrefixTree.getKey(IPV6_DOCUMENTATION_RANGE))
                .isEqualTo(PrefixTree.KEY_MARKER + "0010000000000001" + "0000110110111000")
                .hasSize(33);

        assertThat(PrefixTree.getKey(IPv4_SINGLE_ADDR))
                .isEqualTo(PrefixTree.KEY_MARKER + "11001011" + "00000000" + "01110001" + "11111111")
                .hasSize(33);

        assertThat(PrefixTree.getKey(IPv6_SINGLE_ADDR))
                .isEqualTo(PrefixTree.KEY_MARKER + "0010000000000001" + "0000110110111000" + "0000000000000000".repeat(6))
                .hasSize(129);
    }

    @Test
    void testGetPrefixFromKey() {
        assertThat(subjectV4.rangeFromKey(PrefixTree.getKey(TEST_NET_1)))
                .isEqualTo(TEST_NET_1);

        assertThat(subjectV6.rangeFromKey(PrefixTree.getKey(IPV6_DOCUMENTATION_RANGE)))
                .isEqualTo(IPV6_DOCUMENTATION_RANGE);

        assertThat(subjectV4.rangeFromKey(PrefixTree.getKey(IPv4_SINGLE_ADDR)))
                .isEqualTo(IPv4_SINGLE_ADDR);

        assertThat(subjectV6.rangeFromKey(PrefixTree.getKey(IPv6_SINGLE_ADDR)))
                .isEqualTo(IPv6_SINGLE_ADDR);
    }

    // storing and retrieving data

    @Test
    public void putRejectsIncompatible() {
        assertThatThrownBy(() -> subjectV4.put(IPV6_DOCUMENTATION_RANGE, "test1"))
                .isInstanceOf(AssertionError.class);

        assertThatThrownBy(() -> subjectV6.put(TEST_NET_1, "test1"))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void testInsertAndReadExact() {
        subjectV4.put(TEST_NET_1, "test1");
        subjectV4.put(TEST_NET_1, "test2");

        assertThat(subjectV4.getValueForExactKey(TEST_NET_1))
                .isEqualTo(Set.of("test1", "test2"));
    }

    @Test
    void testInsertAllResources() {
        subjectV4.put(IpRange.ALL_IPV4_RESOURCES, "all IPv4");
        subjectV6.put(IpRange.ALL_IPV6_RESOURCES, "all IPv6");

        assertThat(subjectV4.getValueForExactKey(IpRange.ALL_IPV4_RESOURCES)).isEqualTo(Set.of("all IPv4"));
        assertThat(subjectV6.getValueForExactKey(IpRange.ALL_IPV6_RESOURCES)).isEqualTo(Set.of("all IPv6"));
    }

    @Test
    void testInsertAndReadBelowKey() {
        subjectV4.put(TEST_NET_1, "entry for test_net_1");
        subjectV4.put(TEST_NET_2, "entry for test_net_2");
        subjectV4.put(TEST_NET_3, "entry for test_net_3");

        assertThat(subjectV4.getValuesForKeysStartingWith(IpRange.ALL_IPV4_RESOURCES))
                .hasSize(3)
                .contains(Set.of("entry for test_net_1"))
                .contains(Set.of("entry for test_net_2"))
                .contains(Set.of("entry for test_net_3"));
        // But a smaller prefix does not contain the parent
        assertThat(subjectV4.getValuesForKeysStartingWith(TEST_NET_1))
                .hasSize(1)
                .contains(Set.of("entry for test_net_1"));
    }

    // tracking of stored lengths
    @Test
    void testTracksInsertedLengths() {
        // starts at all 0
        assertThat(subjectV6.getSeenLengths()).doesNotContain(true);

        // insert ::/0 -> first bit should be true
        subjectV6.put(IpRange.ALL_IPV6_RESOURCES, "::/0");
        assertThat(subjectV6.getSeenLengths())
                .hasSize(129)
                .matches(arr -> IntStream.rangeClosed(0, 128).allMatch(i -> arr[i] == Set.of(0).contains(i)));

        subjectV6.put(IPV6_DOCUMENTATION_RANGE, "2001:DB8::/32");
        assertThat(subjectV6.getSeenLengths())
                .hasSize(129)
                .matches(arr -> IntStream.rangeClosed(0, 128).allMatch(i -> arr[i] == Set.of(0, 32).contains(i)));

        subjectV6.put(IPv6_SINGLE_ADDR, "2001:DB8::/128");

        assertThat(subjectV6.getSeenLengths())
                .hasSize(129)
                .matches(arr -> IntStream.rangeClosed(0, 128).allMatch(i -> arr[i] == Set.of(0, 32, 128).contains(i)));
    }
}

