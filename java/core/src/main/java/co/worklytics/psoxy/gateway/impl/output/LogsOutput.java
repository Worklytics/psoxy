package co.worklytics.psoxy.gateway.impl.output;

import co.worklytics.psoxy.gateway.output.Output;
import co.worklytics.psoxy.gateway.ProcessedContent;
import lombok.extern.java.Log;

import java.util.logging.Level;

/**
 * quick dummy output implementation that just logs the content ..
 *
 * not sure there's any *real* use for this, but perhaps
 */
@Log(topic = "output")
public class LogsOutput implements Output {


    @Override
    public void write(ProcessedContent content) {
        log.log(Level.INFO, content.toString());
    }

    @Override
    public void write(String key, ProcessedContent content) {
        log.log(Level.INFO, key + " : " + content.toString());
    }
}
