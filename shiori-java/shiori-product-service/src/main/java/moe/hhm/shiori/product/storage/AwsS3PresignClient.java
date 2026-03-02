package moe.hhm.shiori.product.storage;

import java.time.Duration;
import java.util.Map;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

public class AwsS3PresignClient implements S3PresignClient {

    private final S3Presigner s3Presigner;

    public AwsS3PresignClient(S3Presigner s3Presigner) {
        this.s3Presigner = s3Presigner;
    }

    @Override
    public PresignResult presignPutObject(String bucket, String objectKey, String contentType, Duration expiresIn) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest request = PutObjectPresignRequest.builder()
                .signatureDuration(expiresIn)
                .putObjectRequest(putObjectRequest)
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(request);
        return new PresignResult(
                presigned.url().toString(),
                Map.of("Content-Type", contentType)
        );
    }

    @Override
    public String presignGetObject(String bucket, String objectKey, Duration expiresIn) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(expiresIn)
                .getObjectRequest(getObjectRequest)
                .build();
        return s3Presigner.presignGetObject(request).url().toString();
    }
}
