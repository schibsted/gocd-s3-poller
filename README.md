# Unmaintained, looking for ownership

# gocd-s3-poller

Plugin based on [JSON API](http://www.go.cd/documentation/developer/writing_go_plugins/package_material/json_message_based_package_material_extension.html)
with [gocd/sample-plugins/package-material](https://github.com/gocd/sample-plugins/tree/master/package-material) as base.

## Maven
* Build: `mvn clean package`
* Run tests: `mvn verify`

## Setup
Build it, and copy target/go-plugins-dist/gocd-s3-poller.jar to plugins dir as described in
[Go.cd docs](http://www.go.cd/documentation/developer/writing_go_plugins/go_plugins_basics.html#installing-a-plugin).

Configure the plugin in Admin/Package repository, choose s3-poller and enter a bucket name.
Remember, you need the AWS credentials available in a way [AWS SDK](http://aws.amazon.com/sdk-for-java/) can read them.

Configure it as a Package material in the pipeline, by entering a path the plugin should poll. The folder must exist
and there must be at least one file in that folder.

The poller will trigger the pipeline when a file is added to the given bucket and folder.
It only triggers on files, not folders.

## Todo
* To get latest revision it cycles all files to get the one with the latest modified date. Problematic for buckets with many files.
* Implement pagination support in listObjects, by checking [isTruncated()](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/model/ObjectListing.html#isTruncated()).
