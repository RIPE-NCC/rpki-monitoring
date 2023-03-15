package net.ripe.rpki.monitor.util.http;

import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramChannel;
import io.netty.resolver.*;
import io.netty.resolver.dns.DnsAddressResolverGroup;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.resolver.dns.DnsServerAddressStreamProvider;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

/**
 * Address resolver that implements connect-to by rewriting at the DNS level.
 */
public class ConnectToAddressResolverGroup extends DnsAddressResolverGroup {
    private final Map<String, String> connectTo;

    public ConnectToAddressResolverGroup(Map<String, String> connectTo, DnsNameResolverBuilder dnsResolverBuilder) {
        super(dnsResolverBuilder);
        this.connectTo = Map.copyOf(connectTo);
    }

    @Override
    protected NameResolver<InetAddress> newNameResolver(EventLoop eventLoop, ChannelFactory<? extends DatagramChannel> channelFactory, DnsServerAddressStreamProvider nameServerProvider) throws Exception {
        var delegate = super.newNameResolver(eventLoop, channelFactory, nameServerProvider);
        return new ConnectToNameResolver(delegate);
    }

    private class ConnectToNameResolver implements NameResolver<InetAddress> {
        final NameResolver<InetAddress> delegate;
        public ConnectToNameResolver(NameResolver<InetAddress> delegate) {
            this.delegate =  delegate;
        }

        private String connectTo(String originalInetHost) {
            return connectTo.getOrDefault(originalInetHost, originalInetHost);
        }

        @Override
        public Future<InetAddress> resolve(String inetHost) {
            return delegate.resolve(connectTo(inetHost));
        }

        @Override
        public Future<InetAddress> resolve(String inetHost, Promise<InetAddress> promise) {
            return delegate.resolve(connectTo(inetHost), promise);
        }

        @Override
        public Future<List<InetAddress>> resolveAll(String inetHost) {
            return delegate.resolveAll(connectTo(inetHost));
        }

        @Override
        public Future<List<InetAddress>> resolveAll(String inetHost, Promise<List<InetAddress>> promise) {
            return delegate.resolveAll(connectTo(inetHost), promise);
        }
        @Override
        public void close() {
            delegate.close();
        }
    }
}