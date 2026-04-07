# Atlassian Audit Event / Rovo through Organization API

This connector provides access to Atlassian Audit Event for audit logs and directory information, including Atlassian Rovo usage

## Overview

The Atlassian Organization connector enables data collection from:
- **Audit Events**: Organization-level audit events including Rovo agent interactions, user management activities
- **Audit Events Stream**: Real-time stream of audit events
- **Directory Users**: User information from organization directories

## Authentication

This connector uses API token authentication. See the [Atlassian Organization Admin API Authentication documentation](https://developer.atlassian.com/cloud/admin/organization/rest/intro/#auth) for details.

## Required Scopes

- `read:directories:admin`: For retrieving directory users
- `read:events:admin`: For retrieving audit events and audit events stream

## API Endpoints

### Audit Events
- **Endpoint**: `/admin/v1/orgs/{orgId}/events`
- **Purpose**: Retrieve organization audit events
- **Supported Query Parameters**: cursor, q, from, to, action, actor, ip, product, location, limit

### Audit Events Stream
- **Endpoint**: `/admin/v1/orgs/{orgId}/events-stream`
- **Purpose**: Stream organization audit events in real-time
- **Supported Query Parameters**: cursor, q, from, to, action, actor, ip, product, location, limit

### Directory Users
- **Endpoint**: `/admin/v2/orgs/{orgId}/directories/{directoryId}/users`
- **Purpose**: Retrieve users from organization directories
- **Supported Query Parameters**: cursor, limit, accountIds, directoryIds, resourceIds, groupIds, mfaEnabled, claimStatus, status, accountStatus

## Example API Responses

See the `example-api-responses/original/` directory for sample API responses from each endpoint.

