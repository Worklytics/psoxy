package co.worklytics.psoxy.gateway;


import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;

/**
 * an interface for handling API data requests asynchronously
 *
 * Issues:
 *   - currently, only API data requests that are ultimately SUCCESSFUL will appear in the side output
 *
 *
 */
public interface AsyncApiDataRequestHandler {


    /**
     * handles an API data request asynchronously
     *
     * @param request to handle asynchronously
     */
    void handle(HttpEventRequest request, ApiDataRequestHandler.ProcessingContext processingContext);

}
