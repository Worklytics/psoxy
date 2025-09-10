# Google Cloud Shell

IMPORTANT: Google Cloud Shells are somewhat ephemeral; GCP will delete the `home` directory of your Cloud Shell if you don't use it for ~180 days or so. As such, please be CERTAIN that you 1) [use a remote terraform state backend](https://developer.hashicorp.com/terraform/language/backend) and 2) commit or otherwise backup the Terraform configuration files you create/modify.


## Why Google Cloud Shell?

Google Cloud Shell is implicitly auth'd as your GCP user, which eases running `terraform` to deploy GCP-hosted proxy instances as well as connectors to Google Workspace. So if you're hosting in GCP, or even if you're hosting in AWS but connecting to Google Workspace data, running in GCP cloud shell may be simpler than in a location where you must authenticate with BOTH aws and gcp. 

Cloud Shell offers security benefits over your personal laptop. Even if you use a remote state solution, sensitive information handled by terraform will transit the environment where `terraform` executes. Cloud shell avoids your laptop, local network, and the public internet being in this loop. And there's nothing physical to get lost/stolen/damaged. 

Cloud Shell offers both a terminal and an editor interface via the web. This is more convenient than running terraform in a plain container/VM, where you'd be limited to a terminal and file transfer can be tricky.


## Why not Google Cloud Shell?

Stock dependencies provided by Google are somewhat old; you'll have to update and maintain a few of them to manage the proxy, which may be redundant if you maintain your laptop for terraform/git/java/maven development. As of v0.5.x of the proxy, the stock `terraform` is insufficient; with `v0.6.x`, we anticipate the stock `mvn`/`java` will also be too old.

Per above, GCP is a bit stingy about keeping Google Cloud shell home directories around if not in "active" use; so you need to ensure you log into it periodically. Even if you back-up your Terraform configurations and state to locations outside the shell, if your Cloud Shell instance is de-provisioned by GCP - you'll need to repeat the 'Getting Started' steps again to install all the dependencies/etc.

## Getting Started

1. install `tfenv`, to ease getting proper version of terraform:

```shell
git clone https://github.com/tfutils/tfenv.git ~/.tfenv
mkdir ~/bin
ln -s ~/.tfenv/bin/* ~/bin/
tfenv install
tfenv use latest
```

You should now be ready to continue with the [general setup](https://docs.worklytics.co/psoxy#setup). Those steps should include installation of AWS CLI (for AWS-hosted) and Azure CLI (required for MSFT 365 sources)
