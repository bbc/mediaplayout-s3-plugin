package hudson.plugins.s3;

import hudson.FilePath;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FilenameUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ResponseHeaderOverrides;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.collect.Lists;


import hudson.model.Run;
import hudson.plugins.s3.callable.S3DownloadCallable;
import hudson.plugins.s3.callable.S3UploadCallable;
import hudson.util.Secret;

public class S3Profile {
    private final String name;
    private final String accessKey;
    private final Secret secretKey;
    private final int maxUploadRetries;
    private final int uploadRetryTime;
    private final int maxDownloadRetries;
    private final int downloadRetryTime;
    private transient volatile AmazonS3Client client;

    private final boolean useRole;
    private final int signedUrlExpirySeconds;

    @DataBoundConstructor
    public S3Profile(String name, String accessKey, String secretKey, boolean useRole, int signedUrlExpirySeconds, String maxUploadRetries, String uploadRetryTime, String maxDownloadRetries, String downloadRetryTime) {
        this.name = name;
        this.useRole = useRole;
        this.maxUploadRetries = parseWithDefault(maxUploadRetries, 5);
        this.uploadRetryTime = parseWithDefault(uploadRetryTime, 5);
        this.maxDownloadRetries = parseWithDefault(maxDownloadRetries, 5);
        this.downloadRetryTime = parseWithDefault(downloadRetryTime, 5);
        this.signedUrlExpirySeconds = signedUrlExpirySeconds;
        if (useRole) {
            this.accessKey = "";
            this.secretKey = null;
        } else {
            this.accessKey = accessKey;
            this.secretKey = Secret.fromString(secretKey);
        }
    }

    private int parseWithDefault(String number, int defaultValue) {
        try {
            return Integer.parseInt(number);
        } catch(NumberFormatException nfe) {
            return defaultValue;
        }
    }

    public int getMaxDownloadRetries() {
        return maxDownloadRetries;
    }

    public int getDownloadRetryTime() {
        return downloadRetryTime;
    }

    public final String getAccessKey() {
        return accessKey;
    }

    public final Secret getSecretKey() {
        return secretKey;
    }

    public final int getMaxUploadRetries() {
        return maxUploadRetries;
    }

    public final int getUploadRetryTime() {
        return uploadRetryTime;
    }

    public final String getName() {
        return this.name;
    }

    public final boolean getUseRole() {
        return this.useRole;
    }

    public boolean isUseRole() {
        return useRole;
    }

    public int getSignedUrlExpirySeconds() {
        return signedUrlExpirySeconds;
    }

    public AmazonS3Client getClient() {
        if (client == null) {
            client = ClientHelper.createClient(accessKey, Secret.toString(secretKey), useRole);
        }
        return client;
    }

    public FingerprintRecord upload(Run<?, ?> run,
                                    final String bucketName,
                                    final FilePath filePath,
                                    final String fileName,
                                    final Map<String, String> userMetadata,
                                    final String storageClass,
                                    final String selregion,
                                    final boolean uploadFromSlave,
                                    final boolean managedArtifacts,
                                    final boolean useServerSideEncryption,
                                    final boolean gzipFiles) throws IOException, InterruptedException {
        if (filePath.isDirectory()) {
            throw new IOException(filePath + " is a directory");
        }

        final Destination dest;
        final boolean produced;
        if (managedArtifacts) {
            dest = Destination.newFromRun(run, bucketName, fileName);
            produced = run.getTimeInMillis() <= filePath.lastModified()+2000;
        }
        else {
            dest = new Destination(bucketName, fileName);
            produced = false;
        }

        return repeat(maxUploadRetries, uploadRetryTime, dest, new Callable<FingerprintRecord>() {
            public FingerprintRecord call() throws IOException, InterruptedException {
                S3UploadCallable callable = new S3UploadCallable(produced, fileName, accessKey, secretKey, useRole,
                        bucketName, dest, userMetadata, storageClass, selregion, useServerSideEncryption, gzipFiles);

                if (uploadFromSlave) {
                    return filePath.act(callable);
                } else {
                    return callable.invoke(filePath);
                }
            }
        });
    }

    public List<String> list(Run build, String bucket) {
        AmazonS3Client s3client = getClient();

        String buildName = build.getDisplayName();
        int buildID = build.getNumber();
        Destination dest = new Destination(bucket, "jobs/" + buildName + "/" + buildID + "/" + name);

        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
        .withBucketName(dest.bucketName)
        .withPrefix(dest.objectName);

        List<String> files = Lists.newArrayList();

        ObjectListing objectListing;
        do {
          objectListing = s3client.listObjects(listObjectsRequest);
          for (S3ObjectSummary summary : objectListing.getObjectSummaries()) {
            GetObjectRequest req = new GetObjectRequest(dest.bucketName, summary.getKey());
            files.add(req.getKey());
          }
          listObjectsRequest.setMarker(objectListing.getNextMarker());
        } while (objectListing.isTruncated());
        return files;
      }

      /**
       * Download all artifacts from a given build
       */
      public List<FingerprintRecord> downloadAll(Run build,
                                                 final List<FingerprintRecord> artifacts,
                                                 final String includeFilter,
                                                 final String excludeFilter,
                                                 final FilePath targetDir,
                                                 final boolean flatten) throws IOException, InterruptedException {
          List<FingerprintRecord> fingerprints = Lists.newArrayList();
          for(final FingerprintRecord record : artifacts) {
              final S3Artifact artifact = record.getArtifact();
              final Destination dest = Destination.newFromRun(build, artifact);
              final FilePath target = getFilePath(targetDir, flatten, artifact);

              if (FileHelper.selected(includeFilter, excludeFilter, artifact.getName())) {
                  fingerprints.add(repeat(maxDownloadRetries, downloadRetryTime, dest, new Callable<FingerprintRecord>() {
                      @Override
                      public FingerprintRecord call() throws IOException, InterruptedException {
                          return target.act(new S3DownloadCallable(accessKey, secretKey, useRole, dest, artifact.getRegion()));
                      }
                  }));
              }
          }
          return fingerprints;
      }

    private <T> T repeat(int maxRetries, int waitTime, Destination dest, Callable<T> func) throws IOException, InterruptedException {
        int retryCount = 0;

        while (true) {
            try {
                return func.call();
            } catch (Exception e) {
                retryCount++;
                if(retryCount >= maxRetries){
                    throw new IOException("Call fails for " + dest + ": " + e + ":: Failed after " + retryCount + " tries.", e);
                }
                Thread.sleep(TimeUnit.SECONDS.toMillis(waitTime));
            }
        }
    }

    private FilePath getFilePath(FilePath targetDir, boolean flatten, S3Artifact artifact) {
        if (flatten) {
            return new FilePath(targetDir, FilenameUtils.getName(artifact.getName()));
        }
        else  {
            return new FilePath(targetDir, artifact.getName());
        }
    }

    /**
       * Delete some artifacts of a given run
       * @param run
       * @param record
       */
      public void delete(Run run, FingerprintRecord record) {
          Destination dest = Destination.newFromRun(run, record.getArtifact());
          DeleteObjectRequest req = new DeleteObjectRequest(dest.bucketName, dest.objectName);
          getClient().deleteObject(req);
      }


      /**
       * Generate a signed download request for a redirect from s3/download.
       *
       * When the user asks to download a file, we sign a short-lived S3 URL
       * for them and redirect them to it, so we don't have to proxy for the
       * download and there's no need for the user to have credentials to
       * access S3.
       */
      public String getDownloadURL(Run run, FingerprintRecord record) {
          Destination dest = Destination.newFromRun(run, record.getArtifact());
          GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(dest.bucketName, dest.objectName);
          request.setExpiration(new Date(System.currentTimeMillis() + this.signedUrlExpirySeconds*1000));
          ResponseHeaderOverrides headers = new ResponseHeaderOverrides();
          // let the browser use the last part of the name, not the full path
          // when saving.
          String fileName = (new File(dest.objectName)).getName().trim();
          headers.setContentDisposition("attachment; filename=\"" + fileName + "\"");
          request.setResponseHeaders(headers);
          URL url = getClient().generatePresignedUrl(request);
          return url.toExternalForm();
      }


    @Override
    public String toString() {
        return "S3Profile{" +
                "name='" + name + '\'' +
                ", accessKey='" + accessKey + '\'' +
                ", secretKey=" + secretKey +
                ", useRole=" + useRole +
                '}';
    }

}
