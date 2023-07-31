package net.ripe.rpki.monitor.util;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.IpRange;
import net.ripe.ipresource.IpResource;
import net.ripe.ipresource.IpResourceRange;
import net.ripe.ipresource.IpResourceType;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

@Slf4j
public class IpResourceUtil {
    /**
     * Execute the inner consumer for each component resource of the input resource. These are the prefixes of an IP range,
     * and the bit-aligned ranges of an ASN range or singleton.
     */
    public static <T> Consumer<IpResource> forEachComponentResource(@NonNull Consumer<IpResource> innerConsumer) {
        return resource -> {
            switch (resource) {
                case null -> throw new IllegalArgumentException("Elements can not be null");
                case IpRange range -> range.splitToPrefixes().forEach(innerConsumer);
                case IpResource ipr -> IpResourceUtil.splitToAsnBlocks(ipr).forEach(innerConsumer);
            };
        };
    }

    /**
     * flatMap each component resource (prefixes of IP range, bit-aligned range of AS range) with the provided function.
     */
    public static <T> Function<IpResource, Stream<T>> flatMapComponentResources(@NonNull Function<IpResource, Stream<T>> innerFunction) {
        return (IpResource resource) ->
            (switch(resource) {
                case null -> throw new IllegalArgumentException("Elements can not be null");
                case IpRange range -> range.splitToPrefixes().stream().flatMap(innerFunction);
                case IpResource ipr -> IpResourceUtil.splitToAsnBlocks(ipr).stream().flatMap(innerFunction);
            });
    }

    public static List<IpResource> splitToAsnBlocks(@NonNull IpResource input) {
        assert IpResourceType.ASN.equals(input.getType());

        BigInteger rangeEnd = input.getEnd().getValue();
        BigInteger currentRangeStart = input.getStart().getValue();
        int startingPrefixLength = input.getType().getBitSize();
        List<IpResource> prefixes = new LinkedList<>();

        while (currentRangeStart.compareTo(rangeEnd) <= 0) {
            int maximumPrefixLength = getMaximumLengthOfPrefixStartingAtIpAddressValue(currentRangeStart, startingPrefixLength);
            BigInteger maximumSizeOfPrefix = rangeEnd.subtract(currentRangeStart).add(BigInteger.ONE);
            BigInteger currentSizeOfPrefix = BigInteger.valueOf(2).pow(maximumPrefixLength);

            while ((currentSizeOfPrefix.compareTo(maximumSizeOfPrefix) > 0) && (maximumPrefixLength > 0)) {
                maximumPrefixLength--;
                currentSizeOfPrefix = BigInteger.valueOf(2).pow(maximumPrefixLength);
            }

            BigInteger currentRangeEnd = currentRangeStart.add(BigInteger.valueOf(2).pow(maximumPrefixLength).subtract(BigInteger.ONE));
            var prefix = IpResourceRange.assemble(currentRangeStart, currentRangeEnd, input.getType());

            prefixes.add(prefix);

            currentRangeStart = currentRangeEnd.add(BigInteger.ONE);
        }

        return prefixes;

    }

    private static int getMaximumLengthOfPrefixStartingAtIpAddressValue(BigInteger ipAddressValue, int startingPrefixLength) {
        int prefixLength = startingPrefixLength;

        while ((prefixLength >= 0) && !canBeDividedByThePowerOfTwo(ipAddressValue, prefixLength)) {
            prefixLength--;
        }

        return prefixLength;
    }

    private static boolean canBeDividedByThePowerOfTwo(BigInteger number, int power) {
        return number.remainder(BigInteger.valueOf(2).pow(power)).equals(BigInteger.ZERO);
    }
}
