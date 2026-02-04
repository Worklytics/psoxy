# Install Prerequisites

NOTE: if you cloned one of our examples repos, eg, [GCP](https://github.com/Worklytics/psoxy-example-gcp) or [AWS](https://github.com/Worklytics/psoxy-example-aws), you can run `./check-prereqs` in there to interactively check your environment for the tools you need.


Otherwise, these shell command examples presume Ubuntu; you may need to translate to your *nix variant. If you starting with a fairly rich environment, many of these tools may already be on your machine.

1. install dependencies

```shell
sudo apt update
```

2. install Java + maven (as of v0.5.x, these are optional; you can use a pre-built JAR instead]

```shell
sudo apt install openjdk-17-jdk
sudo apt install maven

# check that maven version at least 3.6+ and java 17+
mvn -v

# if not, get latest direct from Apache Maven
# https://maven.apache.org/install.html
```

3. install Terraform

Follow [Terraform's install guide](https://learn.hashicorp.com/tutorials/terraform/install-cli) (recommended) or, if you need to manage multiple Terraform versions, use `tfenv`:

```shell
git clone https://github.com/tfutils/tfenv.git ~/.tfenv
mkdir ~/bin
sudo apt install unzip
ln -s ~/.tfenv/bin/* ~/bin/
tfenv install
tfenv use latest
```

4. if you're deploying in AWS, install the AWS CLI

```shell
sudo apt install awscli
```

Or see : [https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)

5.if deploying to GCP _or_ using Google Workspace data sources, [install Google Cloud CLI](https://cloud.google.com/sdk/docs/install#linux) and authenticate.

```shell
curl -O https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-cli-384.0.0-linux-x86_64.tar.gz
tar -xvf google-cloud-cli-384.0.0-linux-x86_64.tar.gz
sudo ./google-cloud-sdk/install.sh

# if prompted for location of your .bashrc by the gcloud install script, on EC2 it's '/home/ubuntu/.bashrc'

rm google-cloud-cli-384.0.0-linux-x86_64.tar.gz
```

```shell
# source your .bashrc OR restart your terminal so gcloud is found on your $PATH
source ~/.bashrc

# authenticate with Google Cloud CLI
gcloud auth application-default login --no-launch-browser
```

6. if using Microsoft 365 data sources, install Azure CLI and authenticate.

https://docs.microsoft.com/en-us/cli/azure/install-azure-cli

You should now be ready for the general instructions in the [README.md](README.md).
