# AWS - Getting Started with Cloud Shell

**YMMV; we've found that as of Aug 2022, this is not a great option as people will often hit AWS's
1GB limit for cloudshell. In such a scenario, use your local machine, or a VM/container elsewhere
 in AWS**

1. clone the repo
```shell
git clone https://github.com/Worklytics/psoxy.git
```

2. add the following lines to your `~/.bashrc`. (AWS Cloud Shell preserves only your HOME directory across sessions, so add any commands that modify/install things outside to your `.bashrc`)
```shell

# install Maven (and, via dependency, java)
sudo yum -y install maven

# install AWS Curl (used for testing)
sudo yum -y install pip
pip install awscurl


# GCloud SDK (if using Google Workspace data sources)
# The next line updates PATH for the Google Cloud SDK.
if [ -f '/home/cloudshell-user/google-cloud-sdk/path.bash.inc' ]; then . '/home/cloudshell-user/google-cloud-sdk/path.bash.inc'; fi

# The next line enables shell command completion for gcloud.
if [ -f '/home/cloudshell-user/google-cloud-sdk/completion.bash.inc' ]; then . '/home/cloudshell-user/google-cloud-sdk/completion.bash.inc'; fi
```

Then `source ~/.bashrc`, to execute the above.

3. install Terraform
```shell
git clone https://github.com/tfutils/tfenv.git ~/.tfenv
mkdir ~/bin
ln -s ~/.tfenv/bin/* ~/bin/
tfenv install
tfenv use latest
```

4. if using Google Workspace data sources, [install Google Cloud CLI](https://cloud.google.com/sdk/docs/install#linux) and authenticate.
```shell
curl -O https://dl.google.com/dl/cloudsdk/channels/rapid/downloads/google-cloud-cli-384.0.0-linux-x86_64.tar.gz
tar -xvf google-cloud-cli-384.0.0-linux-x86_64.tar.gz
sudo ./google-cloud-sdk/install.sh
rm google-cloud-cli-384.0.0-linux-x86_64.tar.gz
```

6. if using Microsoft 365 data sources, install Azure CLI and authenticate.

https://docs.microsoft.com/en-us/cli/azure/install-azure-cli


You should now be ready for the general instructions in the [README.md](../../README.md).
