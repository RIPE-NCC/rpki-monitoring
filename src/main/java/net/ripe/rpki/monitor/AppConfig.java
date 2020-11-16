package net.ripe.rpki.monitor;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component("AppConfig")
@Getter
@Setter
public class AppConfig {

    @Autowired
    private RrdpConfig rrdpConfig;

    @Autowired
    @Qualifier("rrdp-resttemplate")
    private RestTemplate restTemplate;

    @Autowired
    private RsyncConfig rsyncConfig;
}