export default {
  asana: {
    name: 'Asana',
    endpoints: [
      {
        name: 'Workspaces',
        path: '/api/1.0/workspaces',
        refs: [
          {
            name: 'Workspace Users',
            accessor: 'data[0].gid',
            paramReplacement: 'workspace',
          },
          {
            name: 'Workspace Projects',
            accessor: 'data[0].gid',
            pathReplacement: 'workspace_id',
          },
        ],
      },
      {
        name: 'Workspace Users',
        path: '/api/1.0/users',
        params: { workspace: null, limit: 10 },
      },
      {
        name: 'Workspace Projects',
        path: '/api/1.0/workspaces/[workspace_id]/projects',
        param: { limit: 10 },
        refs: [
          {
            name: 'Project Tasks',
            accessor: 'data[0].gid',
            pathReplacement: '[project_id]',
          },
        ],
      },
      {
        name: 'Project Tasks',
        path: '/api/1.0/projects/[project_id]/tasks',
        param: { limit: 10 },
        refs: [
          {
            name: 'Task Stories',
            accessor: 'data[0].gid',
            pathReplacement: '[task_id]',
          },
        ],
      },
      {
        name: 'Task Stories',
        path: '/api/1.0/tasks/[task_id]/stories',
      },
    ],
  },
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
  gdirectory: {
    name: 'Google Directory',
    endpoints: [
      {
        name: 'Domains',
        path: '/admin/directory/v1/customer/my_customer/domains',
      },
      {
        name: 'Groups',
        path: '/admin/directory/v1/groups',
        params: { customer: 'my_customer' },
        refs: [
          {
            name: 'Group',
            accessor: 'groups[0].id',
            pathReplacement: '[group_id]',
          },
          {
            name: 'Group Members',
            accessor: 'groups[0].id',
            pathReplacement: '[group_id]',
          },
        ],
      },
      {
        name: 'Group',
        path: '/admin/directory/v1/groups/[group_id]',
      },
      {
        name: 'Group Members',
        path: '/admin/directory/v1/groups/[group_id]/members',
      },
      {
        name: 'Users',
        path: '/admin/directory/v1/users',
        param: { customer: 'my_customer' },
        refs: [
          {
            name: 'User Details',
            accessor: 'users[0].id',
            pathReplacement: '[user_id]',
          },
          {
            name: 'User Thumbnail',
            accessor: 'users[0].id',
            pathReplacement: '[user_id]',
          },
        ],
      },
      {
        name: 'User Details',
        path: '/admin/directory/v1/users/[user_id]',
      },
      {
        name: 'User Thumbnail',
        path: '/admin/directory/v1/users/[user_id]/photos/thumbnail',
      },
      {
        name: 'Roles',
        path: '/admin/directory/v1/customer/my_customer/roles',
      },
      {
        name: 'Role Assingments',
        path: '/admin/directory/v1/customer/my_customer/roleassignments',
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
  gmail: {
    name: 'GMail',
    endpoints: [
      {
        name: 'Messages',
        path: '/gmail/v1/users/me/messages',
        refs: [
          {
            name: 'Message Details',
            accessor: 'messages[0].id',
            pathReplacement: '[message_id]',
          },
        ],
      },
      {
        name: 'Message Details',
        path: '/gmail/v1/users/me/messages/[message_id]',
        params: { format: 'metadata' },
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
      },
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
    ],
  },
};
