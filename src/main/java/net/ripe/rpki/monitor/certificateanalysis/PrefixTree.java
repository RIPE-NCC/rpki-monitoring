package net.ripe.rpki.monitor.certificateanalysis;

import com.google.common.base.Preconditions;
import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultByteArrayNodeFactory;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.*;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

@Slf4j
public class PrefixTree<T> {
    @NonNull
    private final IpResourceType elementType;
    private final ConcurrentRadixTree<Set<T>> tree = new ConcurrentRadixTree<>(new DefaultByteArrayNodeFactory());

   public PrefixTree(IpResourceType elementType) {
       Preconditions.checkArgument(elementType == IpResourceType.IPv4 || elementType == IpResourceType.IPv6);
       this.elementType = elementType;
    }

    public static String getKey(IpRange range) {
        var start = range.getStart().getValue();

        var res = start.toString(2);
        // if res has the incorrect length (leading zero bits), pad with zeros
        var leftPad = range.getType().getBitSize() - res.length();
        if (leftPad > 0) {
            res = "0".repeat(leftPad) + res;
        }

        return res.substring(0, range.getPrefixLength());
    }

    public void put(IpRange key, T value) {
       assert this.elementType == key.getType();
       var element = this.tree.putIfAbsent(getKey(key), new HashSet<T>());

       element.add(value);
    }

    public IpRange rangeFromKey(String key) {
         var prefixLength = key.length();
         // Parse binary string, shift so bits are in correct significant positions.
         var start = new BigInteger(key, 2).shiftLeft(elementType.getBitSize() - prefixLength);

         var ip = switch (this.elementType) {
            case IPv4 -> new Ipv4Address(start.longValue());
            case IPv6 -> new Ipv6Address(start);
            case ASN -> throw new IllegalStateException("invariant violated: element type MUST NOT be ASN");
         };

         return IpRange.prefix(ip, prefixLength);
    }

    public Set<T> getValueForExactKey(IpRange key) {
       return this.tree.getValueForExactKey(getKey(key));
    }

    public Iterable<Set<T>> getValuesForKeysStartingWith(IpRange key) {
       return this.tree.getValuesForKeysStartingWith(getKey(key));
    }
}
