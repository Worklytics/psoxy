# Psoxy Prereq Install

These shell command examples presume Ubuntu; you may need to translate to your *nix variant. If you
starting with a fairly rich environment, many of these tools may already be on your machine.

1. install dependencies

```shell
sudo apt update
```

2. install Java + maven (required to build the proxy binary to be deployed)

```shell
sudo apt install openjdk-11-jdk
sudo apt install maven

# check that maven version at least 3.6+ and java 11+
mvn -v

# if not, get latest direct from Apache Maven
# https://maven.apache.org/install.html
```

3. install Terraform

Follow [Terraform's install guide](https://learn.hashicorp.com/tutorials/terraform/install-cli) (recommended)
or, if you need to manage multiple Terraform versions, use `tfenv`:

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

5. if you want to test an AWS deployment, install AWS Curl (which requires `python` 3.6+ and `pip`)

```shell
# check python version; please ensure it's at least 3.6+
python --version

# if not 3.6+, get latest direct for your environment from Python.org
```

install `pip` (likely included with fresh python install), then use that to install `awscurl`

```shell
sudo apt install pip
pip install awscurl
```

6. if deploying to GCP *or* using Google Workspace data sources, [install Google Cloud CLI](https://cloud.google.com/sdk/docs/install#linux) and authenticate.
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

7. if using Microsoft 365 data sources, install Azure CLI and authenticate.

https://docs.microsoft.com/en-us/cli/azure/install-azure-cli

You should now be ready for the general instructions in the [README.md](../../README.md).
