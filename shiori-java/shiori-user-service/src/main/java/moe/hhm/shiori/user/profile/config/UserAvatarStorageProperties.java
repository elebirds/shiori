package moe.hhm.shiori.user.profile.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "user.avatar.storage")
public class UserAvatarStorageProperties {

    private boolean enabled = true;
    private String endpoint = "http://localhost:9000";
    private String region = "us-east-1";
    private String accessKey = "shiori";
    private String secretKey = "shiori-minio-secret";
    private String bucket = "shiori-user-avatar";
    private boolean pathStyleAccess = true;
    private long maxFileSizeBytes = 2 * 1024 * 1024;
    private String publicPathPrefix = "/api/user/media/avatar/";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public void setPathStyleAccess(boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public String getPublicPathPrefix() {
        return publicPathPrefix;
    }

    public void setPublicPathPrefix(String publicPathPrefix) {
        this.publicPathPrefix = publicPathPrefix;
    }
}
