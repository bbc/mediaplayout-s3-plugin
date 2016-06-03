package hudson.plugins.s3;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import hudson.FilePath;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.logging.Logger;

public final class Uploads {
    private Uploads() {}
    private static final Logger LOGGER = Logger.getLogger(Uploads.class.getName());

    private static transient volatile Uploads instance;
    private final transient HashMap<FilePath, Upload> startedUploads = new HashMap<>();
    private final transient HashMap<FilePath, InputStream> openedStreams = new HashMap<>();

    public void startUploading(TransferManager manager, FilePath file, InputStream inputsStream, String bucketName, String objectName, ObjectMetadata metadata) throws AmazonServiceException {
        final Upload upload = manager.upload(bucketName, objectName, inputsStream, metadata);
        startedUploads.put(file, upload);
        openedStreams.put(file, inputsStream);
    }

    public void finishUploading(FilePath filePath) throws InterruptedException {
        final Upload upload = startedUploads.remove(filePath);
        try {
            upload.waitForCompletion();
        }
        finally {
            closeStream(filePath);
        }
    }

    public void cleanup(FilePath filePath) {
        startedUploads.remove(filePath);
        closeStream(filePath);
    }

    private void closeStream(FilePath filePath) {
        try {
            final InputStream stream = openedStreams.remove(filePath);
            if (stream != null) {
                stream.close();
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to close stream for file:" + filePath);
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
