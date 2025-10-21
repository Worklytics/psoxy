package co.worklytics.psoxy.rules.salesforce;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.salesforce.PrebuiltSanitizerRules;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class SalesforceTests extends JavaRulesTestBaseCase {

    @Getter
    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.SALESFORCE;

    @Getter
    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceKind("salesforce")
        .build();

    @Disabled // not reliable; seems to have different value via IntelliJ/AWS deployment and my
    // laptop's maven, which doesn't make any sense, given that binary deployed to AWS was built via
    // maven on my laptop - so something about Surefire/Junit runner used by maven??
    @Test
    void sha() {
        this.assertSha("7869e465607b7a00b4bd75a832a9ed1f811ce7f2");
    }

    @Test
    @Override
    @Disabled
    public void yamlLength() {
        // Do nothing, as response schema is bigger than we allow for advanced parameters
    }

    @Test
    void account_describe() {
        String jsonString = asJson("account_describe.json");

        String endpoint = "https://test.salesforce.com/services/data/v64.0/sobjects/Account/describe";

        assertUrlWithQueryParamsBlocked(endpoint);
        assertUrlWithSubResourcesBlocked(endpoint);

        this.sanitize(endpoint, jsonString);
    }

    @Test
    void accounts_by_id() {
        String jsonString = asJson("accounts.json");

        String endpoint = "https://test.salesforce.com/services/data/v64.0/composite/sobjects/Account?ids=0015Y00002c7g95QAA,0015Y00002c7g8uQAA&fields=Id,AnnualRevenue,CreatedDate,CreatedById,IsDeleted,LastActivityDate,LastModifiedDate,LastModifiedById,NumberOfEmployees,OwnerId,Ownership,ParentId,Rating,Sic,Type";

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "0055Y00000E16gwQAB");
        assertPseudonymized(sanitized, "0055Y00000E18EHQAZ");
        assertPseudonymized(sanitized, "0055Y00000E16gwQAB");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT+Alias,AccountId,ContactId,CreatedDate,CreatedById,Email,EmailEncodingKey,Id,IsActive,LastLoginDate,LastModifiedDate,ManagerId,Name,TimeZoneSidKey,Username,UserRoleId,UserType+FROM+User+WHERE+LastModifiedDate+%3E%3D+2016-01-01T00%3A00%3A00Z+AND+LastModifiedDate+%3C+2023-03-01T00%3A00%3A00Z+ORDER+BY+LastModifiedDate+DESC+NULLS+LAST",
    })
    void query_users(String query) {
        String jsonString = asJson("users_by_query.json");

        String endpoint = "https://test.salesforce.com/services/data/v64.0/query?q=" + query;

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "0055Y00000ExkfuQAB");
        assertPseudonymized(sanitized, "0055Y00000E16gwQAB");
        assertPseudonymized(sanitized, "0055Y00000E16gwQAB");
        assertPseudonymized(sanitized, "0055Y00000ExkfpQAB");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT+Id,AnnualRevenue,CreatedDate,CreatedById,IsDeleted,LastActivityDate,LastModifiedDate,LastModifiedById,NumberOfEmployees,OwnerId,Ownership,ParentId,Rating,Sic,Type+FROM+Account+WHERE+LastModifiedDate+%3E%3D+2016-01-01T00%3A00%3A00Z+AND+LastModifiedDate+%3C+2023-03-01T00%3A00%3A00Z+ORDER+BY+LastModifiedDate+DESC+NULLS+LAST",
    })
    void query_accounts(String query) {
        String jsonString = asJson("accounts_by_query.json");

        String endpoint = "https://test.salesforce.com/services/data/v64.0/query?q=" + query;

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "0055Y00000E16gwQAB");
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://test.salesforce.com/services/data/v64.0/query?q=SELECT+Alias,AccountId,ContactId,CreatedDate,CreatedById,Email,EmailEncodingKey,Id,IsActive,LastLoginDate,LastModifiedDate,ManagerId,Name,TimeZoneSidKey,Username,UserRoleId,UserType+FROM+User+WHERE+LastModifiedDate+%3E%3D+2016-01-01T00%3A00%3A00Z+AND+LastModifiedDate+%3C+2023-03-01T00%3A00%3A00Z+ORDER+BY+LastModifiedDate+DESC+NULLS+LAST", "users_by_query.json"),
            InvocationExample.of("https://test.salesforce.com/services/data/v64.0/query?q=SELECT%20Id%2CAnnualRevenue%2CCreatedDate%2CCreatedById%2CIsDeleted%2CLastActivityDate%2CLastModifiedDate%2CLastModifiedById%2CNumberOfEmployees%2COwnerId%2COwnership%2CParentId%2CRating%2CSic%2CType%20FROM%20Account%20WHERE%20LastModifiedDate%3E%3D2016-01-01T00%3A00%3A00Z%20AND%20LastModifiedDate%3C2023-03-01T00%3A00%3A00Z%20ORDER%20BY%20LastModifiedDate%20DESC%20NULLS%20LAST", "accounts_by_query.json"),
            InvocationExample.of("https://test.salesforce.com/services/data/v64.0/query/SOME_TOKEN", "users_by_query.json"),

            InvocationExample.of("https://test.salesforce.com/services/data/v64.0/query?q=SELECT+Id,AnnualRevenue,CreatedDate,CreatedById,IsDeleted,LastActivityDate,LastModifiedDate,LastModifiedById,NumberOfEmployees,OwnerId,Ownership,ParentId,Rating,Sic,Type+FROM+Account+WHERE+LastModifiedDate+%3E%3D+2016-01-01T00%3A00%3A00Z+AND+LastModifiedDate+%3C+2023-03-01T00%3A00%3A00Z+ORDER+BY+LastModifiedDate+DESC+NULLS+LAST", "accounts_by_query.json"),
            InvocationExample.of("https://test.salesforce.com/services/data/v64.0/query?q=SELECT+Id,AnnualRevenue,CreatedDate,CreatedById,IsDeleted,LastActivityDate,LastModifiedDate,LastModifiedById,NumberOfEmployees,OwnerId,Ownership,ParentId,Rating,Sic,Type+FROM+Account+WHERE+LastModifiedDate+%3E%3D+2016-01-01T00%3A00%3A00Z+AND+LastModifiedDate+%3C+2023-03-01T00%3A00%3A00Z+ORDER+BY+LastModifiedDate+DESC+NULLS+LAST", "query_result_pagination.json"),
            InvocationExample.of("https://test.salesforce.com/services/data/v64.0/query?q=SELECT%20Id%2CAnnualRevenue%2CCreatedDate%2CCreatedById%2CIsDeleted%2CLastActivityDate%2CLastModifiedDate%2CLastModifiedById%2CNumberOfEmployees%2COwnerId%2COwnership%2CParentId%2CRating%2CSic%2CType%20FROM%20Account%20WHERE%20LastModifiedDate%3E%3D2016-01-01T00%3A00%3A00Z%20AND%20LastModifiedDate%3C2023-03-01T00%3A00%3A00Z%20ORDER%20BY%20LastModifiedDate%20DESC%20NULLS%20LAST", "accounts_by_query.json"),
            InvocationExample.of("https://test.salesforce.com/services/data/v64.0/query/SOME_TOKEN", "accounts_by_query.json"),

            InvocationExample.of("https://test.salesforce.com/services/data/v64.0/composite/sobjects/Account?ids=0015Y00002c7g95QAA%2C0015Y00002c7g8uQAA&fields=Id%2CAnnualRevenue%2CCreatedDate%2CCreatedById%2CIsDeleted%2CLastActivityDate%2CLastModifiedDate%2CLastModifiedById%2CNumberOfEmployees%2COwnerId%2COwnership%2CParentId%2CRating%2CSic%2CType", "accounts.json"),
            InvocationExample.of("https://test.salesforce.com/services/data/v64.0/composite/sobjects/Account?ids=0015Y00002c7g95QAA,0015Y00002c7g8uQAA&fields=Id,AnnualRevenue,CreatedDate,CreatedById,IsDeleted,LastActivityDate,LastModifiedDate,LastModifiedById,NumberOfEmployees,OwnerId,Ownership,ParentId,Rating,Sic,Type", "accounts.json"),

            InvocationExample.of("https://test.salesforce.com/services/data/v64.0/query?q=SELECT+Id,WhoId,WhatId,Subject,ActivityDate,Status,Priority,IsHighPriority,OwnerId,Description,IsDeleted,AccountId,IsClosed,CreatedDate,CreatedById,LastModifiedDate,LastModifiedById,SystemModstamp,IsArchived,CallDurationInSeconds,CallType,CallDisposition,CallObject,ReminderDateTime,IsReminderSet,RecurrenceActivityId,IsRecurrence,RecurrenceStartDateOnly,RecurrenceEndDateOnly,RecurrenceTimeZoneSidKey+FROM+Task+WHERE+AccountId='0015Y00002c7g95QAA'+AND+LastModifiedDate>=2016-01-01T00:00:00Z+AND+LastModifiedDate<2026-01-01T00:00:00Z", "tasks_by_query.json"),
            InvocationExample.of("https://test.salesforce.com/services/data/v64.0/query?q=SELECT%20Id%2CWhoId%2CWhatId%2CSubject%2CActivityDate%2CStatus%2CPriority%2CIsHighPriority%2COwnerId%2CDescription%2CIsDeleted%2CAccountId%2CIsClosed%2CCreatedDate%2CCreatedById%2CLastModifiedDate%2CLastModifiedById%2CSystemModstamp%2CIsArchived%2CCallDurationInSeconds%2CCallType%2CCallDisposition%2CCallObject%2CReminderDateTime%2CIsReminderSet%2CRecurrenceActivityId%2CIsRecurrence%2CRecurrenceStartDateOnly%2CRecurrenceEndDateOnly%2CRecurrenceTimeZoneSidKey%20FROM%20Task%20WHERE%20AccountId%3D%270015Y00002c7g95QAA%27%20AND%20LastModifiedDate%3E%3D2016-01-01T00%3A00%3A00Z%20AND%20LastModifiedDate%3C2026-01-01T00%3A00%3A00Z", "tasks_by_query.json"),
            InvocationExample.of("https://test.salesforce.com/services/data/v64.0/query/SOME_TOKEN", "tasks_by_query.json"),

            InvocationExample.of("https://test.salesforce.com/services/data/v64.0/query?q=SELECT+Id,WhoId,WhatId,Subject,Location,IsAllDayEvent,ActivityDateTime,ActivityDate,DurationInMinutes,StartDateTime,EndDateTime,EndDate,Description,AccountId,OwnerId,IsPrivate,ShowAs,IsDeleted,IsChild,IsGroupEvent,GroupEventType,CreatedDate,CreatedById,LastModifiedDate,LastModifiedById,SystemModstamp,IsArchived,RecurrenceActivityId,IsRecurrence,RecurrenceStartDateTime,RecurrenceEndDateOnly,RecurrenceTimeZoneSidKey,RecurrenceType,RecurrenceInterval,RecurrenceDayOfWeekMask,RecurrenceDayOfMonth,RecurrenceInstance,RecurrenceMonthOfYear,ReminderDateTime,IsReminderSet,EventSubtype,IsRecurrence2Exclusion,Recurrence2PatternText,Recurrence2PatternVersion,IsRecurrence2,IsRecurrence2Exception,Recurrence2PatternStartDate,Recurrence2PatternTimeZone,ServiceAppointmentId+FROM+Event+WHERE+AccountId='0015Y00002c7g8uQAA'+AND+LastModifiedDate>=2016-01-01T00:00:00Z+AND+LastModifiedDate<2026-01-01T00:00:00Z", "events_by_query.json"),
            InvocationExample.of("https://test.salesforce.com/services/data/v64.0/query?q=SELECT%20Id%2CWhoId%2CWhatId%2CSubject%2CLocation%2CIsAllDayEvent%2CActivityDateTime%2CActivityDate%2CDurationInMinutes%2CStartDateTime%2CEndDateTime%2CEndDate%2CDescription%2CAccountId%2COwnerId%2CIsPrivate%2CShowAs%2CIsDeleted%2CIsChild%2CIsGroupEvent%2CGroupEventType%2CCreatedDate%2CCreatedById%2CLastModifiedDate%2CLastModifiedById%2CSystemModstamp%2CIsArchived%2CRecurrenceActivityId%2CIsRecurrence%2CRecurrenceStartDateTime%2CRecurrenceEndDateOnly%2CRecurrenceTimeZoneSidKey%2CRecurrenceType%2CRecurrenceInterval%2CRecurrenceDayOfWeekMask%2CRecurrenceDayOfMonth%2CRecurrenceInstance%2CRecurrenceMonthOfYear%2CReminderDateTime%2CIsReminderSet%2CEventSubtype%2CIsRecurrence2Exclusion%2CRecurrence2PatternText%2CRecurrence2PatternVersion%2CIsRecurrence2%2CIsRecurrence2Exception%2CRecurrence2PatternStartDate%2CRecurrence2PatternTimeZone%2CServiceAppointmentId%20FROM%20Event%20WHERE%20AccountId%3D%270015Y00002c7g8uQAA%27%20AND%20LastModifiedDate%3E%3D2016-01-01T00%3A00%3A00Z%20AND%20LastModifiedDate%3C2026-01-01T00%3A00%3A00Z", "events_by_query.json"),
            InvocationExample.of("https://test.salesforce.com/services/data/v64.0/query/SOME_TOKEN", "events_by_query.json"),

            InvocationExample.of("https://test.salesforce.com/services/data/v64.0/composite", "composite_task_and_events.json")
        );
    }
}
