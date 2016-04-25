package hudson.plugins.s3.callable;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.plugins.s3.Uploads;
import hudson.remoting.VirtualChannel;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;

public final class S3WaitUploadCallable implements FileCallable<Void> {
    @Override
    public Void invoke(File f, VirtualChannel channel) throws InterruptedException, IOException {
        invoke(new FilePath(f));
        return null;
    }

    public Void invoke(FilePath file) throws IOException, InterruptedException {
        Uploads.getInstance().finishUploading(file);
        return null;
    }

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {

    }
}
