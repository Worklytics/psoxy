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
