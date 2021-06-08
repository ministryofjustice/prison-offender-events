package uk.gov.justice.hmpps.offenderevents.subscribe;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SubscribeLocalstackConfig {

    @Bean
    @ConditionalOnProperty(name = "sqs.provider", havingValue = "localstack", matchIfMissing = true)
    @Primary
    public AmazonSQS awsSqsClient(@Value("${sqs.endpoint.url}") String serviceEndpoint,
                                  @Value("${cloud.aws.region.static}") String region) {
        return AmazonSQSClientBuilder.standard()
            .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
            .build();
    }


    @Bean
    @ConditionalOnProperty(name = "sqs.provider", havingValue = "localstack", matchIfMissing = true)
    public AmazonSQS awsSqsDlqClient(@Value("${sqs.endpoint.url}") String serviceEndpoint,
                                  @Value("${cloud.aws.region.static}") String region) {
        return AmazonSQSClientBuilder.standard()
            .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(serviceEndpoint, region))
            .build();
    }

}
