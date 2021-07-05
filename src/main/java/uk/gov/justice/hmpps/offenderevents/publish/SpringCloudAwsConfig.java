package uk.gov.justice.hmpps.offenderevents.publish;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sns.AmazonSNSAsync;
import com.amazonaws.services.sns.AmazonSNSAsyncClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import uk.gov.justice.hmpps.offenderevents.config.SqsConfigProperties;

import static uk.gov.justice.hmpps.offenderevents.config.SqsConfigPropertiesKt.hmppsEventTopic;
import static uk.gov.justice.hmpps.offenderevents.config.SqsConfigPropertiesKt.prisonEventTopic;

@Configuration
@ConditionalOnProperty(name = "hmpps.sqs.provider", havingValue = "aws")
public class SpringCloudAwsConfig {

    @Bean
    @Primary
    public AmazonSNSAsync awsPrisonEventsSnsClient(SqsConfigProperties sqsConfigProperties) {
        var creds = new BasicAWSCredentials(prisonEventTopic(sqsConfigProperties).getTopicAccessKeyId(),
            prisonEventTopic(sqsConfigProperties).getTopicSecretAccessKey());
        return AmazonSNSAsyncClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(creds))
                .withRegion(sqsConfigProperties.getRegion())
                .build();
    }

    @Bean
    public AmazonSNSAsync awsHMPPSEventsSnsClient(SqsConfigProperties sqsConfigProperties) {
        var creds = new BasicAWSCredentials(hmppsEventTopic(sqsConfigProperties).getTopicAccessKeyId(),
            hmppsEventTopic(sqsConfigProperties).getTopicSecretAccessKey());
        return AmazonSNSAsyncClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(creds))
                .withRegion(sqsConfigProperties.getRegion())
                .build();
    }
}
