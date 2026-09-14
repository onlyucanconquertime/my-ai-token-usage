package dev.tokenfarm;

import dev.tokenfarm.config.AppProperties;
import dev.tokenfarm.config.PricingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class TokenFarmApplication {

    public static void main(String[] args) {
        SpringApplication.run(TokenFarmApplication.class, args);
    }

    @Bean
    public PricingProperties pricingProperties(AppProperties appProperties) {
        return appProperties.pricing();
    }
}
