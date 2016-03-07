package hudson.plugins.s3.callable;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.transfer.TransferManager;
import hudson.FilePath.FileCallable;
import hudson.plugins.s3.ClientHelper;
import hudson.plugins.s3.FingerprintRecord;
import hudson.util.Secret;
import org.jenkinsci.remoting.RoleChecker;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;

public abstract class S3Callable implements Serializable, FileCallable<FingerprintRecord>
{
    private static final long serialVersionUID = 1L;

    private final String accessKey;
    private final Secret secretKey;
    private final boolean useRole;
    private final String region;

    private transient static HashMap<String, TransferManager> transferManagers = new HashMap<>();

    public S3Callable(String accessKey, Secret secretKey, boolean useRole, String region)
    {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.useRole = useRole;
        this.region = region;
    }

    protected synchronized TransferManager getTransferManager()
    {
        if (transferManagers.get(region) == null) {
            AmazonS3 client = ClientHelper.createClient(accessKey, Secret.toString(secretKey), useRole, region);
            transferManagers.put(region, new TransferManager(client));
        }

        return transferManagers.get(region);
    }

    public void checkRoles(RoleChecker roleChecker) throws SecurityException {

    }

    public FingerprintRecord generateFingerprint(boolean produced, String bucket, String name, String md5sum) throws IOException {
        return new FingerprintRecord(produced, bucket, name, region, md5sum);
    }
}
