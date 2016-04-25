package hudson.plugins.s3;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import hudson.FilePath;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public final class Uploads {
    private Uploads() {}

    private static transient volatile Uploads instance;
    private final transient HashMap<FilePath, Upload> startedUploads = new HashMap<>();
    private final transient HashMap<FilePath, InputStream> openedStreams = new HashMap<>();

    public void startUploading(TransferManager manager, FilePath file, InputStream inputsStream, String bucketName, String objectName, ObjectMetadata metadata) throws AmazonServiceException {
        final Upload upload = manager.upload(bucketName, objectName, inputsStream, metadata);
        startedUploads.put(file, upload);
        openedStreams.put(file, inputsStream);
    }

    public void finishUploading(FilePath filePath) throws IOException, InterruptedException {
        final Upload upload = startedUploads.remove(filePath);
        try {
            upload.waitForCompletion();
        }
        catch (InterruptedException e) {
            openedStreams.remove(filePath).close();
            throw e;
        }
    }

    public static Uploads getInstance() {
        if (instance == null) {
            synchronized (Uploads.class) {
                if (instance == null) {
                    instance = new Uploads();
                }
            }
        }
        return instance;
    }
}
