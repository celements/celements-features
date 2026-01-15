package com.celements.store.s3;

import java.net.URI;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.xwiki.configuration.ConfigurationSource;

import com.celements.configuration.CelementsAllPropertiesConfigurationSource;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

  private static final Logger LOGGER = LoggerFactory.getLogger(S3Config.class);

  private final ConfigurationSource cfgSrc;

  @Inject
  public S3Config(CelementsAllPropertiesConfigurationSource cfgSrc) {
    this.cfgSrc = cfgSrc;
  }

  @Bean(destroyMethod = "close")
  @Nullable
  public S3Client s3Client() {
    var endpoint = cfgSrc.getProperty("celements.s3.endpoint", "").trim();
    var region = cfgSrc.getProperty("celements.s3.region", "eu-central").trim();
    if (endpoint.isEmpty()) {
      LOGGER.info("S3 endpoint not configured");
      return null;
    }
    var client = S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(buildCredentials()))
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        .build();
    testClient(client);
    LOGGER.info("S3 configured: {}", endpoint);
    return client;
  }

  private void testClient(S3Client client) {
    try {
      client.listBuckets();
    } catch (Exception exc) {
      client.close();
      throw exc;
    }
  }

  private AwsCredentials buildCredentials() {
    var accessKey = cfgSrc.getProperty("celements.s3.accessKey", "").trim();
    var secretKey = cfgSrc.getProperty("celements.s3.secretKey", "").trim();
    if (accessKey.isEmpty() || secretKey.isEmpty()) {
      throw new IllegalArgumentException("s3.accessKey/secretKey missing");
    }
    return AwsBasicCredentials.builder()
        .accessKeyId(accessKey)
        .secretAccessKey(secretKey)
        .build();
  }

  @Bean(name = "s3BucketFilebase")
  @Nullable
  public String s3BucketFilebase(Optional<S3Client> s3Client) {
    var bucket = cfgSrc.getProperty("celements.s3.bucket.filebase", "").trim();
    if (bucket.isEmpty()) {
      LOGGER.info("S3 filebase bucket not configured");
      return null;
    }
    testBucket(s3Client, bucket);
    LOGGER.info("S3 filebase bucket configured: {}", bucket);
    return bucket;
  }

  private void testBucket(Optional<S3Client> s3Client, String bucket) {
    s3Client
        .orElseThrow(() -> new IllegalStateException("S3 client not configured"))
        .headBucket(builder -> builder.bucket(bucket));
  }

}
