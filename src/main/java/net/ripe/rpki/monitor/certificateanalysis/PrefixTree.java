package net.ripe.rpki.monitor.certificateanalysis;

import com.google.common.base.Preconditions;
import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultByteArrayNodeFactory;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.*;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
public class PrefixTree<T> {
    public static final String KEY_MARKER = "P";
    @NonNull
    private final IpResourceType elementType;
    private final ConcurrentRadixTree<Set<T>> tree = new ConcurrentRadixTree<>(new DefaultByteArrayNodeFactory());

    /**
     * Track the length of prefixes seen - used to check what parent prefixes to check for
     *
     * Length is 0 (0/0) - [length of AFI]
     */
    private final boolean[] seenLengths;

   public PrefixTree(IpResourceType resourceType) {
       Preconditions.checkArgument(resourceType == IpResourceType.IPv4 || resourceType == IpResourceType.IPv6);
       this.elementType = resourceType;
       this.seenLengths = new boolean[resourceType.getBitSize() + 1];
    }

    public void put(IpRange prefix, T value) {
        assert this.elementType == prefix.getType();
        Preconditions.checkArgument(prefix.isLegalPrefix(), "Can only store prefix representations in trie");

        var key = getKey(prefix);
        seenLengths[prefix.getPrefixLength()] = true;

        Set<T> newElement = new HashSet<>();
        var curElement = this.tree.putIfAbsent(key, newElement);
        // returns null if missing and it was replaced.
        if (curElement == null) {
            newElement.add(value);
        } else {
            curElement.add(value);
        }
    }

    public Set<T> getValueForExactKey(IpRange key) {
        return this.tree.getValueForExactKey(getKey(key));
    }

    public Iterable<Set<T>> getValuesForKeysStartingWith(IpRange key) {
        return this.tree.getValuesForKeysStartingWith(getKey(key));
    }

    public List<Set<T>> getValuesForEqualOrLessSpecific(IpRange prefix) {
       var fullKey = getKey(prefix);

       return IntStream.rangeClosed(0, prefix.getPrefixLength())
               .filter(length -> seenLengths[length])
               .mapToObj(length -> this.tree.getValueForExactKey(fullKey.substring(0, length + 1)))
               .collect(Collectors.toList());
    }

    /**
     * Format of the keys:
     *   * P - first character to prevent zero length keys
     *   * [bit representation of prefix]
     */
    public static String getKey(IpRange range) {
        var start = range.getStart().getValue();

        var res = start.toString(2);
        // if res has the incorrect length (leading zero bits), pad with zeros
        var leftPad = range.getType().getBitSize() - res.length();
        if (leftPad > 0) {
            res = KEY_MARKER + "0".repeat(leftPad) + res;
        } else {
            res = KEY_MARKER + res;
        }

        return res.substring(0, range.getPrefixLength() + 1);
    }

    /**
     * Extract IpRange from string key.
     */
    public IpRange rangeFromKey(String key) {
        assert key.startsWith(KEY_MARKER);

         var prefixLength = key.length() - 1;
         // Parse binary string, shift so bits are in correct significant positions.
         var start = new BigInteger(key.substring(1), 2).shiftLeft(elementType.getBitSize() - prefixLength);

         var ip = switch (this.elementType) {
            case IPv4 -> new Ipv4Address(start.longValue());
            case IPv6 -> new Ipv6Address(start);
            case ASN -> throw new IllegalStateException("invariant violated: element type MUST NOT be ASN");
         };

         return IpRange.prefix(ip, prefixLength);
    }

    public Set<Integer> getSeenLengths() {
        return IntStream.range(0, seenLengths.length)
                .filter(i -> seenLengths[i])
                .boxed()
                .collect(Collectors.toUnmodifiableSet());
    }
}
