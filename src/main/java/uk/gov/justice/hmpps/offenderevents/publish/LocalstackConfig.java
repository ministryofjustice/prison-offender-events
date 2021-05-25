package uk.gov.justice.hmpps.offenderevents.publish;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sns.AmazonSNSAsync;
import com.amazonaws.services.sns.AmazonSNSAsyncClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class LocalstackConfig {

    @Bean
    @ConditionalOnProperty(name = "sns.provider", havingValue = "localstack", matchIfMissing = true)
    @Primary
    public AmazonSNSAsync awsPrisonEventsSnsClient(@Value("${sns.endpoint.url}") String serviceEndpoint, @Value("${cloud.aws.region.static}") String region) {
        return AmazonSNSAsyncClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
                .build();
    }
    @Bean
    @ConditionalOnProperty(name = "sns.provider", havingValue = "localstack", matchIfMissing = true)
    public AmazonSNSAsync awsHMPPSEventsSnsClient(@Value("${hmpps.sns.endpoint.url}") String serviceEndpoint, @Value("${cloud.aws.region.static}") String region) {
        return AmazonSNSAsyncClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
                .build();
    }
}
