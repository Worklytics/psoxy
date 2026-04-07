package co.worklytics.psoxy.storage.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import com.jayway.jsonpath.Configuration;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class NdjsonRecordReader implements RecordReader {

    final Reader reader;
    final Configuration jsonConfiguration;
    
    BufferedReader bufferedReader;

    @Override
    public Map<String, Object> readRecord() throws IOException {
        if (bufferedReader == null) {
            bufferedReader = new BufferedReader(reader);
        }
        
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            line = StringUtils.trimToNull(line);
            if (line != null) {
                Object parsed = jsonConfiguration.jsonProvider().parse(line);
                if (!(parsed instanceof Map)) {
                    throw new IOException("Supported NDJSON contract requires each line to be a JSON object");
                }
                return (Map<String, Object>) parsed;
            }
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        if (bufferedReader != null) {
            bufferedReader.close();
        } else {
            reader.close();
        }
    }
}
