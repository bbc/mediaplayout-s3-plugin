package hudson.plugins.s3;

import java.io.Serializable;

public final class S3Artifact implements Serializable {
    private static final long serialVersionUID = 1L;

    final private String bucket;
    final private String name;
    final private String region;

    public S3Artifact(String region, String bucket, String name) {
        this.bucket = bucket;
        this.name = name;
        this.region = region;
    }

    public String getBucket() {
        return bucket;
    }

    public String getName() {
        return name;
    }

    public String getRegion() {
        return region;
    }
}