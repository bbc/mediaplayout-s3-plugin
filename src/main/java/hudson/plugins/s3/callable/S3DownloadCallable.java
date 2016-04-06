package hudson.plugins.s3.callable;

import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.transfer.Download;
import hudson.ProxyConfiguration;
import hudson.plugins.s3.Destination;
import hudson.plugins.s3.FingerprintRecord;
import hudson.plugins.s3.MD5;
import hudson.remoting.VirtualChannel;
import hudson.util.Secret;

import java.io.File;
import java.io.IOException;

public class S3DownloadCallable extends S3Callable
{
    private static final long serialVersionUID = 1L;
    private final Destination dest;
    
    public S3DownloadCallable(String accessKey, Secret secretKey, boolean useRole, Destination dest, String region, ProxyConfiguration proxy)
    {
        super(accessKey, secretKey, useRole, region, proxy);
        this.dest = dest;
    }

    @Override
    public FingerprintRecord invoke(File file, VirtualChannel channel) throws IOException, InterruptedException
    {
        final GetObjectRequest req = new GetObjectRequest(dest.bucketName, dest.objectName);
        final Download download = getTransferManager().download(req, file);

        download.waitForCompletion();

        final String md5 = MD5.generateFromFile(file);

        return generateFingerprint(true, dest.bucketName, file.getName(), md5);
    }

}
