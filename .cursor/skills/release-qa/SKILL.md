---
name: release-qa
description: |
  Runs pre-release QA for Psoxy: verify release refs (rc-vX.Y.Z → vX.Y.Z), apply AWS and GCP
  dev examples sequentially, run test-all.sh for both, summarize connector status, create the
  rc-to-main release PR, and post QA results on that PR. Use when cutting a release, running
  release QA, merging rc-v to main, or when the user asks to test connectors before publish.
---

# Release QA

Cut, QA, and local publish orchestration live in the internal `Worklytics/proxy-dev` repo, not in this checkout.

Run the scripts from a psoxy worktree by path (or set `TARGET_PSOXY_CHECKOUT` and invoke from proxy-dev):

```bash
~/code/proxy-dev/tools/release/prep.sh rc-vX.Y.Z vX.Y.Z
~/code/proxy-dev/tools/release/run-release-qa.sh vX.Y.Z
~/code/proxy-dev/tools/release/rc-to-main.sh vX.Y.Z
```

Follow `tools/release/release-qa.md` and `.cursor/skills/release-qa/SKILL.md` in **proxy-dev** for the full checklist.

GitHub Actions in this repo still publish Maven packages and deployment bundles on `v*` tags and `rc-*` branches (`tools/release/` scripts that remain here).
