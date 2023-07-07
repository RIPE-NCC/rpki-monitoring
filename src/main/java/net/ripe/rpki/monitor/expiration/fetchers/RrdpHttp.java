package net.ripe.rpki.monitor.expiration.fetchers;

import lombok.Getter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;

public interface RrdpHttp {
    byte[] fetch(String uri) throws HttpTimeout, HttpResponseException;

    String overrideHostname(String url);

    default String describe() {
        return "N/A";
    }

    @Getter
    sealed class HttpException extends Exception permits HttpTimeout, HttpResponseException {
        private final HttpMethod method;
        private final String uri;

        private final RrdpHttp client;

        public HttpException(RrdpHttp client, HttpMethod method, String uri, Exception cause) {
            super(cause);
            this.method = method;
            this.uri = uri;
            this.client = client;
        }
    }
    /**
     * HTTP timeout: Not considered to be a critical failure for RRDP when it happens infrequently.
     */
    @Getter
    final class HttpTimeout extends HttpException {
        public HttpTimeout(RrdpHttp client, HttpMethod method, String uri, Exception cause) {
            super(client, method, uri, cause);
        }
        public HttpTimeout(RrdpHttp client, String uri, Exception cause) {
            this(client, null, uri, cause);
        }
    }

    /**
     * An invalid HTTP response (any non-200). A failure that indicates a broken RRDP endpoint.
     */
    @Getter
    final class HttpResponseException extends HttpException {
        private final HttpStatusCode statusCode;

        public HttpResponseException(RrdpHttp client, HttpMethod method, String uri, HttpStatusCode statusCode, Exception cause) {
            super(client, method, uri, cause);
            this.statusCode = statusCode;
        }
    }
}
