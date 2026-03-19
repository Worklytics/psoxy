# AWS - Getting Started with Cloud Shell

**YMMV; as of June 2023, AWS's 1GB limit on cloud shell persistent storage is too low for real world proxy deployments, which typically require install gcloud CLI / Azure CLI to connect to sources**

**SO instead, we suggest to use your local machine, or a VM/container elsewhere in AWS (EC2, AWS Cloud9, etc**

1. clone the repo

```shell
git clone https://github.com/Worklytics/psoxy-example-aws.git
```

2. install Terraform, at min.

```shell
git clone https://github.com/tfutils/tfenv.git ~/.tfenv
mkdir ~/bin
ln -s ~/.tfenv/bin/* ~/bin/
tfenv install
tfenv use latest
```

3. if using Google Workspace data sources, [install Google Cloud CLI](https://cloud.google.com/sdk/docs/install#linux) and authenticate.

```shell
curl -O https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-cli-435.0.1-linux-x86_64.tar.gz
tar -xvf google-cloud-cli-435.0.1-linux-x86_64.tar.gz
sudo ./google-cloud-sdk/install.sh
rm google-cloud-cli-435.0.1-linux-x86_64.tar.gz
```

then add the following lines to your `~/.bashrc`. (AWS Cloud Shell preserves only your HOME directory across sessions, so add any commands that modify/install things outside to your `.bashrc`)

```shell
# GCloud SDK (if using Google Workspace data sources)
# The next line updates PATH for the Google Cloud SDK.
if [ -f '/home/cloudshell-user/google-cloud-sdk/path.bash.inc' ]; then . '/home/cloudshell-user/google-cloud-sdk/path.bash.inc'; fi

# The next line enables shell command completion for gcloud.
if [ -f '/home/cloudshell-user/google-cloud-sdk/completion.bash.inc' ]; then . '/home/cloudshell-user/google-cloud-sdk/completion.bash.inc'; fi
```

Then `source ~/.bashrc`, to execute the above.

4. if using Microsoft 365 data sources, install Azure CLI and authenticate.

https://docs.microsoft.com/en-us/cli/azure/install-azure-cli

You should now be ready for the general instructions in the [README.md](../../README.md).

5. Install NodeJS, if you want to use our test tooling

[NodeJS](https://nodejs.org/en/download/)