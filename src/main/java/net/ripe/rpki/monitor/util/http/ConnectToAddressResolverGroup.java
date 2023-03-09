package net.ripe.rpki.monitor.util.http;

import io.netty.resolver.*;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.SocketUtils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Address resolver that implements connect-to by rewriting at the DNS level.
 */
public class ConnectToAddressResolverGroup extends AddressResolverGroup<InetSocketAddress>{
    private final Map<String, String> connectTo;

    public ConnectToAddressResolverGroup(Map<String, String> connectTo) {
        this.connectTo = Map.copyOf(connectTo);
    }

    @Override
    protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) throws Exception {
        return new ConnectToDefaultAddressResolver(executor).asAddressResolver();
    }

    private class ConnectToDefaultAddressResolver extends InetNameResolver {
        public ConnectToDefaultAddressResolver(EventExecutor executor) {
            super(executor);
        }

        private String connectTo(String originalInetHost) {
            return connectTo.getOrDefault(originalInetHost, originalInetHost);
        }

        @Override
        protected void doResolve(String inetHost, Promise<InetAddress> promise) throws Exception {
            try {
                promise.setSuccess(SocketUtils.addressByName(connectTo(inetHost)));
            } catch (UnknownHostException e) {
                promise.setFailure(e);
            }
        }

        @Override
        protected void doResolveAll(String inetHost, Promise<List<InetAddress>> promise) throws Exception {
            try {
                promise.setSuccess(Arrays.asList(SocketUtils.allAddressesByName(connectTo(inetHost))));
            } catch (UnknownHostException e) {
                promise.setFailure(e);
            }
        }
    }
}