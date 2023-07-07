package net.ripe.rpki.monitor.expiration.fetchers;

import lombok.Data;
import lombok.Getter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;

import java.util.Optional;

public interface RrdpHttpStrategy {
    byte[] fetch(String uri) throws HttpTimeout, HttpResponseException;

    String overrideHostname(String url);

    default String describe() {
        return "N/A";
    }

    /**
     * HTTP timeout: Not considered to be a critical failure for RRDP when it happens infrequently.
     */
    @Getter
    final class HttpTimeout extends Exception {
        private final HttpMethod method;
        private final String uri;

        private final RrdpHttpStrategy client;

        public HttpTimeout(RrdpHttpStrategy client, HttpMethod method, String uri, Exception cause) {
            super(cause);
            this.client = client;
            this.method = method;
            this.uri = uri;
        }
        public HttpTimeout(RrdpHttpStrategy client, String uri, Exception cause) {
            this(client, null, uri, cause);

        }
    }

    /**
     * An invalid HTTP response (any non-200). A failure that indicates a broken RRDP endpoint.
     */
    @Getter
    final class HttpResponseException extends Exception {
        private final RrdpHttpStrategy client;

        private final HttpMethod method;
        private final HttpStatusCode statusCode;

        private final String uri;

        public HttpResponseException(RrdpHttpStrategy client, HttpMethod method, String uri, HttpStatusCode statusCode, Exception cause) {
            super(cause);
            this.client = client;
            this.method = method;
            this.uri = uri;
            this.statusCode = statusCode;
        }
    }
}
