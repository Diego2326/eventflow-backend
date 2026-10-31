package com.eventflow.eventflow_api.storage.infrastructure.storage

import com.eventflow.eventflow_api.storage.application.ObjectStorage
import com.eventflow.eventflow_api.storage.application.DirectObjectStorage
import com.eventflow.eventflow_api.storage.application.StoredObjectMetadata
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.core.sync.RequestBody
import java.net.URI
import java.time.Duration

@Configuration(proxyBeanMethods = false)
class NeonObjectStorageConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "app.storage", name = ["provider"], havingValue = "neon")
    fun neonS3Client(
        @Value("\${AWS_ACCESS_KEY_ID:}") accessKeyId: String,
        @Value("\${AWS_SECRET_ACCESS_KEY:}") secretAccessKey: String,
        @Value("\${AWS_ENDPOINT_URL_S3:}") endpoint: String,
        @Value("\${AWS_REGION:}") region: String,
    ): S3Client {
        require(accessKeyId.isNotBlank() && secretAccessKey.isNotBlank() && endpoint.isNotBlank() && region.isNotBlank()) {
            "Neon Object Storage requires AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_ENDPOINT_URL_S3, and AWS_REGION"
        }
        return S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey),
                ),
            )
            .serviceConfiguration(
                S3Configuration.builder().pathStyleAccessEnabled(true).build(),
            )
            .build()
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "app.storage", name = ["provider"], havingValue = "neon")
    fun neonS3Presigner(
        @Value("\${AWS_ACCESS_KEY_ID:}") accessKeyId: String,
        @Value("\${AWS_SECRET_ACCESS_KEY:}") secretAccessKey: String,
        @Value("\${AWS_ENDPOINT_URL_S3:}") endpoint: String,
        @Value("\${AWS_REGION:}") region: String,
    ): S3Presigner {
        val credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKeyId, secretAccessKey),
        )
        return S3Presigner.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(credentials)
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.storage", name = ["provider"], havingValue = "neon")
    fun neonObjectStorage(
        s3Client: S3Client,
        s3Presigner: S3Presigner,
        @Value("\${app.storage.bucket:uploads}") bucket: String,
    ): NeonObjectStorage = NeonObjectStorage(s3Client, s3Presigner, bucket)
}

class NeonObjectStorage(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val bucket: String,
) : ObjectStorage, DirectObjectStorage {
    override val configured: Boolean = bucket.isNotBlank()

    override fun put(path: String, contentType: String, bytes: ByteArray) {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(path)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(bytes),
        )
    }

    override fun get(path: String): ByteArray = s3Client.getObjectAsBytes(
        GetObjectRequest.builder().bucket(bucket).key(path).build(),
    ).asByteArray()

    override fun delete(path: String) {
        s3Client.deleteObject(
            DeleteObjectRequest.builder().bucket(bucket).key(path).build(),
        )
    }

    override fun createUploadUrl(path: String, contentType: String, lifetime: Duration): String {
        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(path)
            .contentType(contentType)
            .build()
        return s3Presigner.presignPutObject(
            PutObjectPresignRequest.builder()
                .signatureDuration(lifetime)
                .putObjectRequest(request)
                .build(),
        ).url().toExternalForm()
    }

    override fun createDownloadUrl(path: String, lifetime: Duration): String {
        val request = GetObjectRequest.builder().bucket(bucket).key(path).build()
        return s3Presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(lifetime)
                .getObjectRequest(request)
                .build(),
        ).url().toExternalForm()
    }

    override fun metadata(path: String): StoredObjectMetadata? = try {
        val head = s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(path).build())
        StoredObjectMetadata(head.contentLength(), head.contentType())
    } catch (missing: S3Exception) {
        if (missing.statusCode() == 404) null else throw missing
    }
}
