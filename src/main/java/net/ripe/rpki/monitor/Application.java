package net.ripe.rpki.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@Slf4j
@ConfigurationPropertiesScan("net.ripe.rpki.monitor")
@SpringBootApplication
public class Application {
	@Autowired
	private MonitorProperties properties;

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean(name = "rrdp-resttemplate")
	public RestTemplate restTemplate(RestTemplateBuilder builder) {
		return builder
				.defaultHeader("user-agent", String.format("rpki-monitor %s", properties.getVersion()))
				.build();
	}

	@Bean(name = "rpki-core-resttemplate")
	public RestTemplate coreClient(RestTemplateBuilder builder) {
		return builder
				.defaultHeader("user-agent", String.format("rpki-monitor %s", properties.getVersion()))
				.defaultHeader(MonitorProperties.INTERNAL_API_KEY_HEADER, properties.getCore().getApiKey())
				.rootUri(properties.getCore().getUrl())
				.build();
	}
}
