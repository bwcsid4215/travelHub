package com.bwc.travel_request_management.config;

import io.minio.MinioClient;
import io.minio.MakeBucketArgs;
import io.minio.BucketExistsArgs;
import io.minio.SetBucketPolicyArgs;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final MinioProperties properties;

    @Bean
    public MinioClient minioClient() {
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(properties.getEndpoint())
                    .credentials(properties.getAccessKey(), properties.getSecretKey())
                    .build();

            // 🪣 Check and create bucket if missing
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.getDefaultBucket()).build()
            );

            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(properties.getDefaultBucket()).build());
                System.out.println("✅ Created bucket: " + properties.getDefaultBucket());
            } else {
                System.out.println("🟢 Bucket already exists: " + properties.getDefaultBucket());
            }

            // 🌍 Optional — Automatically set the bucket to public access
            String policy = """
                {
                  "Version":"2012-10-17",
                  "Statement":[{
                    "Effect":"Allow",
                    "Principal":{"AWS":["*"]},
                    "Action":["s3:GetObject"],
                    "Resource":["arn:aws:s3:::%s/*"]
                  }]
                }
                """.formatted(properties.getDefaultBucket());

            client.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(properties.getDefaultBucket())
                            .config(policy)
                            .build()
            );

            System.out.println("🌍 Public access policy applied for bucket: " + properties.getDefaultBucket());

            return client;

        } catch (Exception e) {
            throw new RuntimeException("❌ Failed to initialize MinIO client: " + e.getMessage(), e);
        }
    }
}
