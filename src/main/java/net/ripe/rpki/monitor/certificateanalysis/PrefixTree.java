package net.ripe.rpki.monitor.certificateanalysis;

import com.google.common.base.Preconditions;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.*;
import org.apache.commons.collections4.trie.PatriciaTrie;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
public class PrefixTree<T> {
    public static final String KEY_MARKER = "P";
    @NonNull
    private final IpResourceType elementType;

    private final PatriciaTrie<Set<T>> trie = new PatriciaTrie<>();

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
        assert value != null;
        assert this.elementType == prefix.getType();
        assert prefix.isLegalPrefix(); // Can only store prefix representations in trie

        var key = getKey(prefix);
        seenLengths[prefix.getPrefixLength()] = true;

        this.trie.compute(key, (k, curElement) -> {
            if (curElement == null) {
                Set<T> newElement = new HashSet<>();
                newElement.add(value);
                return newElement;
            } else {
                curElement.add(value);
                return curElement;
            }
        });
    }

    public Set<T> getValueForExactKey(IpRange key) {
        return this.trie.get(getKey(key));
    }

    public Iterable<Set<T>> getValuesForKeysStartingWith(IpRange key) {
       return this.trie.prefixMap(getKey(key)).values();
    }

    public List<Set<T>> getValuesForEqualOrLessSpecific(IpRange prefix) {
       var fullKey = getKey(prefix);

       var out = new ArrayList<Set<T>>(prefix.getPrefixLength());

       for (int i=0; i < prefix.getPrefixLength(); i++) {
           if (seenLengths[i]) {
               var res = this.trie.get(fullKey.substring(0, i+1));
                if (res != null) {
                     out.add(res);
                }
           }
       }

       return out;
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
