package com.schibsted.gocd.s3poller;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.schibsted.gocd.s3poller.message.CheckConnectionResultMessage;
import com.schibsted.gocd.s3poller.message.PackageMaterialProperties;
import com.schibsted.gocd.s3poller.message.PackageRevisionMessage;
import com.thoughtworks.go.plugin.api.logging.Logger;

import java.util.List;

import static java.util.Arrays.asList;

public class PackageRepositoryPoller {

    private final Logger log = Logger.getLoggerFor(this.getClass());
    private PackageRepositoryConfigurationProvider configurationProvider;
    private AmazonS3Client client;

    public PackageRepositoryPoller(PackageRepositoryConfigurationProvider configurationProvider, AmazonS3Client client) {
        this.configurationProvider = configurationProvider;
        this.client = client;
    }

    public CheckConnectionResultMessage checkConnectionToRepository(PackageMaterialProperties repositoryConfiguration) {
        String bucketName = repositoryConfiguration.getProperty(Constants.S3_BUCKET).value();
        Boolean bucketExists = false;
        try {
            bucketExists = client.doesBucketExist(bucketName);
        } catch (Exception ex) {
            return new CheckConnectionResultMessage(
                CheckConnectionResultMessage.STATUS.FAILURE,
                asList("Could not find bucket. [" + ex.getMessage() + "]"));
        }
        if (bucketExists) {
            return new CheckConnectionResultMessage(CheckConnectionResultMessage.STATUS.SUCCESS, asList("Bucket found"));
        }
        return new CheckConnectionResultMessage(CheckConnectionResultMessage.STATUS.FAILURE, asList("Bucket not found"));
    }

    public CheckConnectionResultMessage checkConnectionToPackage(PackageMaterialProperties packageConfiguration, PackageMaterialProperties repositoryConfiguration) {
        String bucketName = repositoryConfiguration.getProperty(Constants.S3_BUCKET).value();
        String path = packageConfiguration.getProperty(Constants.S3_PATH).value();
        ObjectListing listing;
        try {
            listing = client.listObjects(bucketName, path);
        } catch (Exception ex) {
            return new CheckConnectionResultMessage(
                CheckConnectionResultMessage.STATUS.FAILURE,
                asList("Could not find path '" + path + "' in bucket '" + bucketName + "'. [" + ex.getMessage() + "]"));
        }
        if (!listing.getObjectSummaries().isEmpty()) {
            return new CheckConnectionResultMessage(CheckConnectionResultMessage.STATUS.SUCCESS, asList("Objects found on path"));
        }
        return new CheckConnectionResultMessage(
            CheckConnectionResultMessage.STATUS.FAILURE,
            asList("Could not find objects in path. Folder can't be empty."));
    }

    public PackageRevisionMessage getLatestRevision(PackageMaterialProperties packageConfiguration, PackageMaterialProperties repositoryConfiguration) {
        String bucketName = repositoryConfiguration.getProperty(Constants.S3_BUCKET).value();
        String path = packageConfiguration.getProperty(Constants.S3_PATH).value();
        try {
            S3ObjectSummary latest = getLatestS3Object(bucketName, path);
            if (latest != null) {
                log.info("Latest object: key=" + latest.getKey() + ", modified=" + latest.getLastModified());
                return new PackageRevisionMessage(
                        latest.getKey(),
                        latest.getLastModified(),
                        "S3",
                        "Object at " + latest.getKey() + " with date " + latest.getLastModified().toString(),
                        client.getUrl(bucketName, latest.getKey()).toString()
                );
            }
        } catch (Exception e) {
            log.info("Failed to get latest revision: bucketName=" + bucketName + ", prefix=" + path, e);
        }
        return new PackageRevisionMessage();
    }

    private S3ObjectSummary getLatestS3Object(String bucketName, String prefix) {
        S3ObjectSummary latestSummary = null;
        ObjectListing listing = null;
        int numBatches = 100;

        do {
            listing = listing == null ? client.listObjects(bucketName, prefix) : client.listNextBatchOfObjects(listing);
            List<S3ObjectSummary> summaries = listing.getObjectSummaries();
            for (S3ObjectSummary summary : summaries) {
                if (latestSummary == null || summary.getLastModified().after(latestSummary.getLastModified())) {
                    latestSummary = summary;
                }
            }
        } while (listing.isTruncated() && --numBatches > 0);

        return latestSummary;
    }

    public PackageRevisionMessage getLatestRevisionSince(PackageMaterialProperties packageConfiguration, PackageMaterialProperties repositoryConfiguration, PackageRevisionMessage previousPackageRevision) {
        PackageRevisionMessage prm = getLatestRevision(packageConfiguration, repositoryConfiguration);
        if (prm.getTimestamp().after(previousPackageRevision.getTimestamp())) {
            return prm;
        }
        return null;

    }
}
