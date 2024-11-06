# Create a private fork

If you want to make private (non-public) customization to Psoxy's source/terraform modules, you may
wish to create a private fork of the repo. (if you intend to commit your changes, a public fork in
GitHub should suffice)

See
[Duplicating a Repo](https://docs.github.com/en/repositories/creating-and-managing-repositories/duplicating-a-repository),
for guidance.

Specific commands for Psoxy repo are below:

```shell
# set up the mirror
git clone --bare https://github.com/Worklytics/psoxy.git
cd psoxy
git push --mirror https://github.com/{{YOUR_GITHUB_ORG_ID}}/psoxy-private.git
cd ..
rm -rf psoxy
git clone https://github.com/{{YOUR_GITHUB_ORG_ID}}/psoxy-private.git

# set the public repo as 'upstream' remote
git remote add upstream git@github.com:worklytics/psoxy.git
git remote set-url --push upstream DISABLE

# fetch, rebase on top of your work
git fetch upstream
git rebase upstream/main
```
