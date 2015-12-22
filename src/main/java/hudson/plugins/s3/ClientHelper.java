package hudson.plugins.s3;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;

import java.util.regex.Pattern;

public class ClientHelper {
    private static ClientConfiguration clientConfiguration;

    public static AmazonS3Client createClient(String accessKey, String secretKey, boolean useRole)
    {
        if (useRole) {
            return new AmazonS3Client();
        } else {
            return new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey), getClientConfiguration());
        }
    }

    private static synchronized ClientConfiguration getClientConfiguration() {
        if (clientConfiguration == null) {
            clientConfiguration = new ClientConfiguration();

            ProxyConfiguration proxy = Jenkins.getInstance().proxy;
            if (shouldUseProxy(proxy, "s3.amazonaws.com")) {
                clientConfiguration.setProxyHost(proxy.name);
                clientConfiguration.setProxyPort(proxy.port);
                if(proxy.getUserName() != null) {
                    clientConfiguration.setProxyUsername(proxy.getUserName());
                    clientConfiguration.setProxyPassword(proxy.getPassword());
                }
            }
        }
        return clientConfiguration;
    }

    private static boolean shouldUseProxy(ProxyConfiguration proxy, String hostname) {
        if(proxy == null) {
            return false;
        }

        boolean shouldProxy = true;
        for(Pattern p : proxy.getNoProxyHostPatterns()) {
            if(p.matcher(hostname).matches()) {
                shouldProxy = false;
                break;
            }
        }

        return shouldProxy;
    }
}
