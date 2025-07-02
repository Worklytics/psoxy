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
     * TODO: refactor this; if we're parsing HttpEventRequest --> sourceApiRequest before "accepting" it for processing, then pass that in here instead
     * of requestToProxy ??
     *    - drawback is that makes flow for sync/async handling different
     *
     *    but esp in GCP case, if that's handled in-process, then *re-processing* the requestToProxy --> sourceApiRequest is painful.
     *
     * @param requestToProxy to handle asynchronously
     */
    void handle(HttpEventRequest requestToProxy, ApiDataRequestHandler.ProcessingContext processingContext);

}
