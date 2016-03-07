package hudson.plugins.s3;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;

import java.util.regex.Pattern;

public class ClientHelper {
    public static AmazonS3Client createClient(String accessKey, String secretKey, boolean useRole)
    {
        return createClient(accessKey, secretKey, useRole, null);
    }

    public static AmazonS3Client createClient(String accessKey, String secretKey, boolean useRole, String region)
    {
        final AmazonS3Client client;

        if (useRole) {
            client = new AmazonS3Client();
        } else {
            client = new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey), getClientConfiguration());
        }

        if (region != null)
        {
            client.setRegion(getRegionFromString(region));
        }

        return client;
    }

    private static synchronized ClientConfiguration getClientConfiguration() {
        ClientConfiguration clientConfiguration = new ClientConfiguration();

        ProxyConfiguration proxy = Jenkins.getInstance().proxy;
        if (shouldUseProxy(proxy, "s3.amazonaws.com")) {
            clientConfiguration.setProxyHost(proxy.name);
            clientConfiguration.setProxyPort(proxy.port);
            if (proxy.getUserName() != null) {
                clientConfiguration.setProxyUsername(proxy.getUserName());
                clientConfiguration.setProxyPassword(proxy.getPassword());
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

    private static Region getRegionFromString(String regionName) {
        // In 0.7, selregion comes from Regions#name
        Region region = RegionUtils.getRegion(regionName);

        // In 0.6, selregion comes from Regions#valueOf
        if (region == null) {
            region = RegionUtils.getRegion(Regions.valueOf(regionName).getName());
        }

        return region;
    }
}
