package hudson.plugins.s3.callable;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.transfer.TransferManager;
import hudson.FilePath.FileCallable;
import hudson.plugins.s3.ClientHelper;
import hudson.plugins.s3.FingerprintRecord;
import hudson.util.Secret;
import org.jenkinsci.remoting.RoleChecker;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

public abstract class S3Callable implements Serializable, FileCallable<FingerprintRecord>
{
    private static final long serialVersionUID = 1L;

    private final String accessKey;
    private final Secret secretKey;
    private final boolean useRole;

    private transient static volatile AmazonS3Client client;
    private transient static volatile TransferManager transferManager;
    private transient static String oldClientHash;

    public S3Callable(String accessKey, Secret secretKey, boolean useRole)
    {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.useRole = useRole;
    }

    protected synchronized AmazonS3Client getClient()
    {
        String newClientHash = getHash();

        if (client == null || !newClientHash.equals(oldClientHash)) {
            client = ClientHelper.createClient(accessKey, Secret.toString(secretKey), useRole);
            oldClientHash = newClientHash;
        }

        return client;
    }

    private String getHash() {
        return accessKey + "|" + Secret.toString(secretKey) + "|" + Boolean.toString(useRole);
    }

    protected synchronized TransferManager getTransferManager()
    {
        if (transferManager == null) {
            transferManager = new TransferManager(getClient());
        }
        else {
            AmazonS3 oldClient = transferManager.getAmazonS3Client();
            AmazonS3 newClient = getClient();
            if (!newClient.equals(oldClient)) {
                transferManager.shutdownNow(true);
                transferManager = new TransferManager(getClient());
            }
        }

        return transferManager;
    }

    protected String getMD5(InputStream inputStream) throws IOException {
        return org.apache.commons.codec.digest.DigestUtils.md5Hex(inputStream);
    }

    public void checkRoles(RoleChecker roleChecker) throws SecurityException {

    }
}
