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

## Development

Use the docker [gocd-dev](https://hub.docker.com/r/gocd/gocd-dev/) container and launch it using 
`docker run -i -t -p 8153:8153 gocd/gocd-dev`.

Build the project `mvn clean package` and use the command shown on the docker page to copy the jar to the GoCD server 
docker container.

You can then ssh into the container and inspect logs at `/var/log/go-server`.

A simple way to add your AWS credentials during development is to add the following lines to the 
`PackageRepositoryMaterial` class constructor:

    public PackageRepositoryMaterial() {
        configurationProvider = new PackageRepositoryConfigurationProvider();
        packageRepositoryPoller = new PackageRepositoryPoller(configurationProvider, new AmazonS3Client(new AWSCredentials() {
            @Override
            public String getAWSAccessKeyId() {
                return "<YOUR_AWS_ACCESS_KEY_ID>";
            }

            @Override
            public String getAWSSecretKey() {
                return "<YOUR_AWS_SECRET_KEY>";
            }
        }));
