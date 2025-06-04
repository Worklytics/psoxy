package co.worklytics.psoxy.gateway.impl.output;

import co.worklytics.psoxy.gateway.Output;
import co.worklytics.psoxy.gateway.ProcessedContent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.java.Log;

import javax.inject.Inject;
import java.util.Collection;
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
    public void batchWrite(Collection<ProcessedContent> contents) {
        for (ProcessedContent content : contents) {
            write(content);
        }
    }
}
