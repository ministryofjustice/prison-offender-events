package uk.gov.justice.hmpps.offenderevents.subscribe;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class SubscribeSpringCloudAwsConfig {

    @Bean
    @ConditionalOnProperty(name = "sqs.provider", havingValue = "aws")
    @Primary
    public AmazonSQS awsSqsClient(@Value("${sns.aws.access.key.id}") String accessKey,
                                  @Value("${sns.aws.secret.access.key}") String secretKey,
                                  @Value("${cloud.aws.region.static}") String region) {
        return AmazonSQSClientBuilder.standard()
            .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)))
            .withRegion(region)
            .build();
    }
}
