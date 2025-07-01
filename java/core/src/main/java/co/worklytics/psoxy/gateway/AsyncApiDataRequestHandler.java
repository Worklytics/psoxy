package co.worklytics.psoxy.gateway;

public interface AsyncApiDataRequestHandler {


    /**
     * handles an API data request asynchronously
     *
     * @param request to handle asynchronously
     */
    void handle(HttpEventRequest request);
}
