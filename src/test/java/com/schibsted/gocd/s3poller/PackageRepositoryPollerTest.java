package com.schibsted.gocd.s3poller;
import com.amazonaws.AmazonClientException;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.Owner;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.schibsted.gocd.s3poller.message.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PackageRepositoryPollerTest {

    @Mock (answer = Answers.RETURNS_DEEP_STUBS)
    AmazonS3Client client;

    PackageRepositoryConfigurationProvider prcp;
    PackageRepositoryPoller prp;
    PackageMaterialProperties pmp;

    @Before
    public void setUp() {
        prcp = new PackageRepositoryConfigurationProvider();
        prp = new PackageRepositoryPoller(prcp, client);
        pmp = new PackageMaterialProperties();
    }

    @Test
    public void checkConnectionToRepositoryBucketExistTest() {

        when(client.doesBucketExist(anyString())).thenReturn(true);

        pmp.addPackageMaterialProperty(
            Constants.S3_BUCKET,
            new PackageMaterialProperty().withValue("mybucket"));
        CheckConnectionResultMessage ccrm = prp.checkConnectionToRepository(pmp);
        assertTrue(ccrm.success());
    }

    @Test
    public void checkConnectionToRepositoryBucketNonExistTest() {

        when(client.doesBucketExist(anyString())).thenReturn(false);

        pmp.addPackageMaterialProperty(
                Constants.S3_BUCKET,
                new PackageMaterialProperty().withValue("faultybucket"));
        CheckConnectionResultMessage ccrm = prp.checkConnectionToRepository(pmp);
        assertFalse(ccrm.success());
    }

    @Test
    public void checkConnectionToRepositoryFaultyClientTest() {

        when(client.doesBucketExist(anyString())).thenThrow(new AmazonClientException("message"));

        pmp.addPackageMaterialProperty(
                Constants.S3_BUCKET,
                new PackageMaterialProperty().withValue("faultybucket"));
        CheckConnectionResultMessage ccrm = prp.checkConnectionToRepository(pmp);
        assertFalse(ccrm.success());
    }

    @Test
    public void checkConnectionToPackageObjectExistTest() {

        when(client.listObjects("bucket", "path").getObjectSummaries()).thenReturn(getObjectsInBucket(2));

        pmp.addPackageMaterialProperty(
                Constants.S3_BUCKET,
                new PackageMaterialProperty().withValue("bucket"));
        pmp.addPackageMaterialProperty(
                Constants.S3_PATH,
                new PackageMaterialProperty().withValue("path"));
        CheckConnectionResultMessage ccrm = prp.checkConnectionToPackage(pmp, pmp);
        assertTrue(ccrm.success());
    }

    @Test
    public void checkConnectionToPackageObjectNonExistTest() {

        when(client.listObjects("bucket", "path").getObjectSummaries()).thenReturn(getObjectsInBucket(0));

        pmp.addPackageMaterialProperty(
                Constants.S3_BUCKET,
                new PackageMaterialProperty().withValue("bucket"));
        pmp.addPackageMaterialProperty(
                Constants.S3_PATH,
                new PackageMaterialProperty().withValue("path"));
        CheckConnectionResultMessage ccrm = prp.checkConnectionToPackage(pmp, pmp);
        assertFalse(ccrm.success());
    }

    @Test
    public void checkConnectionToPackageObjectFaultyClientTest() {

        when(client.listObjects(anyString(), anyString())).thenThrow(new AmazonClientException("Some message"));

        pmp.addPackageMaterialProperty(
                Constants.S3_BUCKET,
                new PackageMaterialProperty().withValue("bucket"));
        pmp.addPackageMaterialProperty(
                Constants.S3_PATH,
                new PackageMaterialProperty().withValue("path"));
        CheckConnectionResultMessage ccrm = prp.checkConnectionToPackage(pmp, pmp);
        assertFalse(ccrm.success());
    }

    @Test
    public void getLatestRevisionTest() throws MalformedURLException {

        when(client.listObjects(anyString(), anyString()).getObjectSummaries()).thenReturn(
            getObjectsInBucket(new Date(10000), new Date(50000), new Date(30000), new Date(20000))
        );
        when(client.getUrl(anyString(), anyString())).thenReturn(new URL("http://example.domain/path/to/file.zip"));
        pmp.addPackageMaterialProperty(
                Constants.S3_BUCKET,
                new PackageMaterialProperty().withValue("bucket"));
        pmp.addPackageMaterialProperty(
                Constants.S3_PATH,
                new PackageMaterialProperty().withValue("path"));
        PackageRevisionMessage prm = prp.getLatestRevision(pmp, pmp);
        assertEquals(new Date(50000), prm.getTimestamp());
    }

    @Test
    public void getLatestRevisionSinceWithNewFileTest() throws MalformedURLException {

        when(client.listObjects(anyString(), anyString()).getObjectSummaries()).thenReturn(
                getObjectsInBucket(new Date(10000), new Date(50000), new Date(30000), new Date(20000))
        );
        when(client.getUrl(anyString(), anyString())).thenReturn(new URL("http://example.domain/path/to/file.zip"));
        pmp.addPackageMaterialProperty(
                Constants.S3_BUCKET,
                new PackageMaterialProperty().withValue("bucket"));
        pmp.addPackageMaterialProperty(
                Constants.S3_PATH,
                new PackageMaterialProperty().withValue("path"));
        // String revision, Date timestamp, String user, String revisionComment, String trackbackUrl
        PackageRevisionMessage prevPrm = new PackageRevisionMessage("1", new Date(40000), "username", "comment", "url");
        PackageRevisionMessage prm = prp.getLatestRevisionSince(pmp, pmp, prevPrm);
        assertEquals(new Date(50000), prm.getTimestamp());
    }

    @Test
    public void getLatestRevisionByPagingThoughAllListings() throws Exception {
        ObjectListing objectListingOne = mock(ObjectListing.class);
        ObjectListing objectListingTwo = mock(ObjectListing.class);

        when(client.getUrl(anyString(), anyString())).thenReturn(new URL("http://example.domain/path/to/file.zip"));

        when(objectListingOne.isTruncated()).thenReturn(true);
        when(objectListingOne.getObjectSummaries()).thenReturn(
                getObjectsInBucket(new Date(20000)));

        when(objectListingTwo.isTruncated()).thenReturn(false);
        when(objectListingTwo.getObjectSummaries()).thenReturn(
                getObjectsInBucket(new Date(50000)));

        when(client.listObjects(anyString(), anyString())).thenReturn(objectListingOne);
        when(client.listNextBatchOfObjects(objectListingOne)).thenReturn(objectListingTwo);

        pmp.addPackageMaterialProperty(Constants.S3_BUCKET, new PackageMaterialProperty().withValue("bucket"));
        pmp.addPackageMaterialProperty(Constants.S3_PATH, new PackageMaterialProperty().withValue("path"));

        // String revision, Date timestamp, String user, String revisionComment, String trackbackUrl
        PackageRevisionMessage prevPrm = new PackageRevisionMessage("1", new Date(40000), "username", "comment", "url");
        PackageRevisionMessage prm = prp.getLatestRevisionSince(pmp, pmp, prevPrm);

        verify(client, times(1)).listNextBatchOfObjects(any(ObjectListing.class));
        assertEquals(new Date(50000), prm.getTimestamp());
    }

    @Test
    public void getLatestRevisionSinceWithNoNewFileTest() throws MalformedURLException {

        when(client.listObjects(anyString(), anyString()).getObjectSummaries()).thenReturn(
                getObjectsInBucket(new Date(10000), new Date(50000), new Date(30000), new Date(20000))
        );
        when(client.getUrl(anyString(), anyString())).thenReturn(new URL("http://example.domain/path/to/file.zip"));
        pmp.addPackageMaterialProperty(
                Constants.S3_BUCKET,
                new PackageMaterialProperty().withValue("bucket"));
        pmp.addPackageMaterialProperty(
                Constants.S3_PATH,
                new PackageMaterialProperty().withValue("path"));
        // String revision, Date timestamp, String user, String revisionComment, String trackbackUrl
        PackageRevisionMessage prevPrm = new PackageRevisionMessage("1", new Date(50000), "username", "comment", "url");
        PackageRevisionMessage prm = prp.getLatestRevisionSince(pmp, pmp, prevPrm);
        assertNull(prm);
    }

    private List<S3ObjectSummary> getObjectsInBucket(Integer count) {
        List<S3ObjectSummary> list = new ArrayList<S3ObjectSummary>();
        for (Integer i = 0; i < count; i++) {
            list.add(new S3ObjectSummary());
        }
        return list;
    }

    private List<S3ObjectSummary> getObjectsInBucket(Date... dates) {
        List<S3ObjectSummary> list = new ArrayList<S3ObjectSummary>();
        for (Date date : dates) {
            S3ObjectSummary s3Object = new S3ObjectSummary();
            s3Object.setKey("dummy-key");
            s3Object.setLastModified(date);
            s3Object.setOwner(new Owner("123", "username"));
            list.add(s3Object);
        }
        return list;
    }

}
