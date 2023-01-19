package com.avaulta.gateway.rules.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for responses of endpoints to be included in the ruleset
 *
 *  q: how to deal with generics? (eg, endpoint response type is ResultsPage<Task>??)
 *   I guess worst case we create concrete class (`class TaskResultsPage extends ResultsPage<Task> {  }`)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EndpointResponse {


    //eg "google-workspace/gcal"
    String source();

    //any regex against path
    String pathRegex();

    //eg "/calendar/{calendarId}/events"
    String pathTemplate();

}
