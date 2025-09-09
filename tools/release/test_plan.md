## Test Plan

### AWS

```shell
cd infra/examples-dev/aws
./apply
./test-all.sh
```

Confirm everything worked:
- [ ] Microsoft API connector
- [ ] Google Workspace API connector
- [ ] Token-based API connector
- [ ] API connector with async
- [ ] Webhook collector
- [ ] Bulk connector

### GCP
```shell
cd infra/examples-dev/gcp
./apply
./test-all.sh
```

Confirm everything worked:
- [ ] Microsoft API connector
- [ ] Google Workspace API connector
- [ ] Token-based API connector
- [ ] API connector with async
- [ ] Webhook collector
- [ ] Bulk connector
- [ ] Scan a GCP container image for vulnerabilities:

```shell
./tools/gcp/container-scan.sh psoxy-dev-erik psoxy-dev-erik-gcal
```

