export default {
  gcal: {
    name: 'Google Calendar',
    endpoints: [
      {
        name: 'Primary',
        path: '/calendar/v3/calendars/primary',
      },
      {
        name: 'Settings',
        path: '/calendar/v3/users/me/settings',
      },
      {
        name: 'Events',
        path: '/calendar/v3/calendars/primary/events',
        refs: [
          {
            name: 'Event',
            accessor: 'items[0].id',
            pathReplacement: '[event_id]',
          },
        ],
      },
      {
        name: 'Event',
        path: '/calendar/v3/calendars/primary/events/[event_id]',
      },
    ],
  },
  gdrive: {
    name: 'Google Drive',
    endpoints: [
      {
        name: 'Files v2 API',
        path: '/drive/v2/files',
        refs: [
          {
            name: 'File Details v2 API',
            accessor: 'files[0].id',
            pathReplacement: '[file_id]',
          },
          {
            name: 'File Revisions',
            accessor: 'files[0].id',
            pathReplacement: '[file_id]',
          },
          {
            name: 'File Permissions',
            accessor: 'files[0].id',
            pathReplacement: '[file_id]',
          },
          {
            name: 'File Comments',
            accessor: 'files[0].id',
            pathReplacement: '[file_id]',
          },
          {
            name: 'File Comment Details',
            accessor: 'files[0].id',
            pathReplacement: '[file_id]',
          },
          {
            name: 'File Comment Replies',
            accessor: 'files[0].id',
            pathReplacement: '[file_id]',
          },
        ],
      },
      {
        name: 'Files v3 API',
        path: '/drive/v3/files',
        refs: [
          {
            name: 'File Details v3 API',
            accessor: 'files[0].id',
            pathReplacement: '[file_id]',
          },
        ],
      },
      {
        name: 'File Details v2 API',
        path: '/drive/v2/files/[file_id]',
        params: { fields: '*' },
      },
      {
        name: 'File Details v3 API',
        path: '/drive/v3/files/[file_id]',
        params: { fields: '*' },
      },
      {
        name: 'File Revisions',
        path: '/drive/v2/files/[file_id]/revisions',
      },
      {
        name: 'File Permissions',
        path: '/drive/v2/files/[file_id]/permissions',
      },
      {
        name: 'File Comments',
        path: '/drive/v2/files/[file_id]/comments',
        refs: [
          {
            name: 'File Comment Details',
            accessor: 'items[0].id',
            pathReplacement: '[comment_id]',
          },
          {
            name: 'File Comment Replies',
            accessor: 'items[0].id',
            pathReplacement: '[comment_id]',
          },
        ],
      },
      {
        name: 'File Comment Details',
        path: '/drive/v2/files/[file_id]/comments/[comment_id]',
      },
      {
        name: 'File Comment Replies',
        path: '/drive/v2/files/[file_id]/comments/[comment_id]/replies',
      },
    ],
  },
  'google-chat': {
    name: 'Google Chat',
    endpoints: [
      {
        name: 'Messages',
        path: '/admin/reports/v1/activity/users/all/applications/chat',
        params: { maxResults: 10 },
      }
    ],
  },
  'google-meet': {
    name: 'Google Meet',
    endpoints: [
      {
        name: 'Messages',
        path: '/admin/reports/v1/activity/users/all/applications/meet',
        params: { maxResults: 10 },
      },
    ],
  },
  'slack-discovery-api': {
    name: 'Slack Discovery API',
    endpoints: [
      {
        name: 'Workspaces',
        path: '/api/discovery.enterprise.info',
        refs: [
          {
            name: 'Workspace Conversations',
            accessor: 'enterprise.teams[0].id',
            paramReplacement: 'team',
          },
          {
            name: 'Workspace Channel Messages',
            accessor: 'enterprise.teams[0].id',
            paramReplacement: 'team',
          },
          {
            name: 'Workspace Channel Info',
            accessor: 'enterprise.teams[0].id',
            paramReplacement: 'team',
          },
        ],
      },
      {
        name: 'Users',
        path: '/api/discovery.users.list',
        params: { include_deleted: true },
      },
      {
        name: 'Workspace Conversations',
        path: '/api/discovery.conversations.list',
        params: { limit: 10, team: null },
        refs: [
          {
            name: 'Workspace Channel Messages',
            accessor: 'channels[0].id',
            paramReplacement: 'channel',
          },
          {
            name: 'Channel Direct Messages',
            accessor: 'channels[0].id',
            paramReplacement: 'channel',
          },
          {
            name: 'Workspace Channel Info',
            accessor: 'channels[0].id',
            paramReplacement: 'channel',
          },
        ],
      },
      {
        name: 'Workspace Channel Messages',
        path: '/api/discovery.conversations.history',
        params: { limit: 10, team: null, channel: null },
      },
      {
        name: 'Direct Messages',
        path: '/api/discovery.conversations.list',
        params: { limit: 10 },
      },
      {
        name: 'Channel Direct Messages',
        path: '/api/discovery.conversations.history',
        params: { limit: 10, channel: null },
      },
      {
        name: 'Workspace Channel Info',
        path: '/api/discovery.conversations.info',
        params: { limit: 1, team: null, channel: null },
      },
    ],
  },
  zoom: {
    name: 'Zoom',
    endpoints: [
      {
        name: 'Users',
        path: '/v2/users',
      },
    ]
  }
};
