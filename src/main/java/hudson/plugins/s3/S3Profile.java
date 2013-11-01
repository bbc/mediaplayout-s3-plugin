package hudson.plugins.s3;

import hudson.FilePath;
import hudson.FilePath.FileCallable;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.internal.Mimetypes;

import hudson.remoting.VirtualChannel;
import hudson.util.Secret;

public class S3Profile {
    private String name;
    private String accessKey;
    private Secret secretKey;
    private transient volatile AmazonS3Client client = null;

    public S3Profile() {
    }

    @DataBoundConstructor
    public S3Profile(String name, String accessKey, String secretKey) {
        this.name = name;
        this.accessKey = accessKey;
        this.secretKey = Secret.fromString(secretKey);
        client = new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey));
    }

    public final String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public final Secret getSecretKey() {
        return secretKey;
    }

    public final String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AmazonS3Client getClient() {
        if (client == null) {
            client = new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey.getPlainText()));
        }
        return client;
    }

    public void check() throws Exception {
        getClient().listBuckets();
    }

    public void upload(String bucketName, FilePath filePath, int searchPathLength, List<MetadataPair> userMetadata,
            String storageClass, String selregion, boolean uploadFromSlave) throws IOException, InterruptedException {
        if (filePath.isDirectory()) {
            throw new IOException(filePath + " is a directory");
        }

        String relativeFileName = filePath.getRemote();
        relativeFileName = relativeFileName.substring(searchPathLength);

        final Destination dest = new Destination(bucketName, relativeFileName);

        try {
            S3UploadCallable callable = new S3UploadCallable(getClient(), dest, userMetadata, storageClass, selregion);
            if (uploadFromSlave) {
                filePath.act(callable);
            } else {
                callable.invoke(filePath);
            }
        } catch (Exception e) {
            throw new IOException("put " + dest + ": " + e);
        }
    }

    public static class S3UploadCallable implements FileCallable<Void> {
        private static final long serialVersionUID = 1L;
        private final Destination dest;
        private final AmazonS3Client client;
        private final String storageClass;
        private List<MetadataPair> userMetadata;
        private String selregion;

        public S3UploadCallable(AmazonS3Client client, Destination dest, List<MetadataPair> userMetadata, String storageClass,
                String selregion) {
            this.dest = dest;
            this.client = client;
            this.storageClass = storageClass;
            this.userMetadata = userMetadata;
            this.selregion = selregion;
        }

        public ObjectMetadata buildMetadata(FilePath filePath) throws IOException, InterruptedException {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(Mimetypes.getInstance().getMimetype(filePath.getName()));
            metadata.setContentLength(filePath.length());
            metadata.setLastModified(new Date(filePath.lastModified()));
            if ((storageClass != null) && !"".equals(storageClass)) {
                metadata.setHeader("x-amz-storage-class", storageClass);
            }
            for (MetadataPair metadataPair : userMetadata) {
                metadata.addUserMetadata(metadataPair.key, metadataPair.value);
            }
            return metadata;
        }

        /**
         * Upload from slave directly
         */
        public Void invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
            invoke(new FilePath(file));
            return null;
        }

        /**
         * Stream from slave to master, then upload from master
         */
        public Void invoke(FilePath file) throws IOException, InterruptedException {
            setRegion();
            client.putObject(dest.bucketName, dest.objectName, file.read(), buildMetadata(file));
            return null;
        }

        private void setRegion() {
            Region region = RegionUtils.getRegion(Regions.valueOf(selregion).getName());
            client.setRegion(region);
        }
    }

}
