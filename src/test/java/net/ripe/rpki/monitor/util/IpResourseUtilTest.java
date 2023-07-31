package net.ripe.rpki.monitor.util;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResource;
import net.ripe.ipresource.IpResourceRange;
import net.ripe.ipresource.IpResourceSet;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
public class IpResourseUtilTest {
    // An IP address range that breaks into two prefixes, and an AS range that breaks
    // into two components.
    private static final IpResourceSet DECOMPOSABLE_RESOURCES = IpResourceSet.parse("10.0.0.0-10.0.2.255, AS64496-AS64498");

    private static final List<IpResource> COMPONENT_RESOURCES = List.of(
            IpResource.parse("10.0.0.0/23"),
            IpResource.parse("10.0.2.0/24"),
            IpResource.parse("AS64496-AS64497"),
            IpResource.parse("AS64498")
    );
    @Test
    void testForEachComponentResource() {
        var components = Sets.newHashSet();
        DECOMPOSABLE_RESOURCES.stream().forEach(IpResourceUtil.forEachComponentResource(components::add));

        assertThat(components)
                .containsExactlyInAnyOrderElementsOf(COMPONENT_RESOURCES);
    }

    @Test
    void testFlatMapComponentResources() {
        var components = DECOMPOSABLE_RESOURCES.stream()
                .flatMap(IpResourceUtil.flatMapComponentResources(Stream::of))
                .toList();

        assertThat(components)
                .containsExactlyInAnyOrderElementsOf(COMPONENT_RESOURCES);
    }

    @Test
    void testSplitAsnRanges_singleton() {
        var singleElement = IpResource.parse("AS64496-64496");

        assertThat(IpResourceUtil.splitToAsnBlocks(singleElement))
                .containsExactly(singleElement);
    }

    @Test
    void testSplitAsnRange_16b() {
        // The documentation range is aligned on bit boundaries
        var alignedRange = IpResource.parse("AS64496-64511");

        assertThat(IpResourceUtil.splitToAsnBlocks(alignedRange))
                .hasSize(1)
                .containsExactly(alignedRange);

        // Not a documentation range, but documentation ranges are aligned for 16b ASNs
        var unAlignedRange = IpResource.parse("AS64496-65520");
        var splitRange = IpResourceUtil.splitToAsnBlocks(unAlignedRange);

        assertThat(elementsOfSameSizeAreNotAdjacent(splitRange)).isTrue();

        assertThat(splitRange)
                .hasSizeGreaterThan(3)
                // check that endpoints are present
                .anyMatch(res -> res.contains(IpResource.parse("AS64496")))
                .anyMatch(res -> res.contains(IpResource.parse("AS65520")));

        assertThat(ImmutableResourceSet.of(splitRange)).isEqualTo(ImmutableResourceSet.of(unAlignedRange));
    }

    @Test
    void testSplitAsnRange_32b() {
        // aligned documentation range at the beginning of 32b asn space
        var alignedRange = IpResource.parse("AS65536-65551");

        assertThat(IpResourceUtil.splitToAsnBlocks(alignedRange))
                .hasSize(1)
                .containsExactly(alignedRange);

        // Now use the large, non-aligned documentation range
        var largeRange = IpResource.parse("AS4200000000-4294967294");
        var largeRangeSplits = IpResourceUtil.splitToAsnBlocks(largeRange);

        assertThat(elementsOfSameSizeAreNotAdjacent(largeRangeSplits)).isTrue();

        assertThat(largeRangeSplits)
                // It splits into a "reasonable" number of ranges: Detect trivial breakdown into chunks of 1/128/other small numbers
                .hasSizeLessThan(64)
                // The ranges are powers of two
                // recall: if there is only one bit set in the binary representation of a number it is a power of two.
                .allMatch(elem -> elem.getEnd().getValue().subtract(elem.getStart().getValue()).add(BigInteger.ONE).bitCount() == 1);

        assertThat(ImmutableResourceSet.of(largeRangeSplits)).isEqualTo(ImmutableResourceSet.of(largeRange));
    }

    @Test
    void testAssertElementsSameSizeAreNotAdjacent() {
        assertThat(elementsOfSameSizeAreNotAdjacent(List.of(IpResource.parse("AS64496")))).isTrue();
        assertThat(elementsOfSameSizeAreNotAdjacent(List.of(IpResource.parse("AS64496"), IpResource.parse("AS64498")))).isTrue();

        assertThat(elementsOfSameSizeAreNotAdjacent(List.of(IpResource.parse("AS64496"), IpResource.parse("AS64497")))).isFalse();
    }

    /**
     * If there are multiple elements of the same size, they are not adjacent
     */
    boolean elementsOfSameSizeAreNotAdjacent(List<IpResource> elements) {
        return elements.stream()
                .collect(Collectors.groupingBy(elem -> elem.getEnd().getValue().subtract(elem.getStart().getValue()).add(BigInteger.ONE)))
                .values().stream()
                .allMatch(elems -> {
                    if (elems.size() > 1) {
                        return Sets.combinations(Set.copyOf(elems), 2).stream().noneMatch(subset -> {
                            var iter = subset.iterator();
                            return iter.next().adjacent(iter.next());
                        });
                    }
                    return true;
                });
    }
}
