package net.ripe.rpki.monitor.fetchers;

import lombok.AllArgsConstructor;
import lombok.Setter;
import net.ripe.rpki.monitor.expiration.fetchers.RrdpHttp;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@AllArgsConstructor
public class FakeRrdpHttp implements RrdpHttp {
    @Setter
    List<String> paths;
    AtomicInteger filesRead = new AtomicInteger(0);

    @Override
    public byte[] fetch(String uri) throws HttpTimeout, HttpResponseException {
        var pos = filesRead.getAndIncrement();
        var path = paths.get(pos % paths.size());
        try {
            return new ClassPathResource(path).getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String transformHostname(String url) {
        return url;
    }
}
