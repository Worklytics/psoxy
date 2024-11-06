# Getting Started with Google Cloud Shell

1. clone the repo (or a [private-fork](../development/private-fork.md) of it)

```shell
git clone https://github.com/Worklytics/psoxy.git
```

2. if using Microsoft 365 sources, install and authenticate Azure CLI

https://docs.microsoft.com/en-us/cli/azure/install-azure-cli

3. if deploying AWS infra,
   [install](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) and
   authenticate AWS CLI

```shell
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install
```

You should now be ready for the general instructions in the [README.md](../README.md).
