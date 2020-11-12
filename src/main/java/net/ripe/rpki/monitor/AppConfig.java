package net.ripe.rpki.monitor;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component("AppConfig")
@Getter
@Setter
public class AppConfig {

    @Value("${rrdp.url}")
    private String rrdpUrl;

    @Value("${rsync.timeout}")
    private int rsyncTimeout;

    @Value("${rsync.url}")
    private String rsyncUrl;

    @Autowired
    @Qualifier("rrdp-resttemplate")
    private RestTemplate restTemplate;

    @Autowired
    private RsyncConfig rsyncConfig;
}