#!/bin/bash

# resets example to state prior to `./init`
rm .terraform.lock.hcl 2>/dev/null
rm build 2>/dev/null
rm update-bundle 2>/dev/null
rm psoxy-* 2>/dev/null
rm -rf .terraform 2>/dev/null
rm terraform.tfvars 2>/dev/null