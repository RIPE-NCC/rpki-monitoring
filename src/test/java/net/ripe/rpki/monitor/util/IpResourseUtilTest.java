package net.ripe.rpki.monitor.util;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.ImmutableResourceSet;
import net.ripe.ipresource.IpResource;
import net.ripe.ipresource.IpResourceRange;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Slf4j
public class IpResourseUtilTest {
    @Test
    void testSplitAsnRanges_singleton() {
        var singleElement = IpResource.parse("AS64496-64496");

        assertThat(IpResourceUtil.splitToAsnBlocks(singleElement))
                .containsExactly(singleElement);
    }

    @Test
    void testSplitAsnRange_16b() {
        // Not a documentation range, but documentation ranges are aligned for 16b ASNs
        var alignedRange = IpResource.parse("AS64496-65520");
        var splitRange = IpResourceUtil.splitToAsnBlocks(alignedRange);

        assertThat(elementsOfSameSizeAreNotAdjacent(splitRange)).isTrue();

        assertThat(splitRange)
                .hasSizeGreaterThan(3)
                // check that endpoints are present
                .anyMatch(res -> res.contains(IpResource.parse("AS64496")))
                .anyMatch(res -> res.contains(IpResource.parse("AS65520")));

        assertThat(ImmutableResourceSet.of(splitRange)).isEqualTo(ImmutableResourceSet.of(alignedRange));
    }

    @Test
    void testSplitAsnRange_32b() {
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
