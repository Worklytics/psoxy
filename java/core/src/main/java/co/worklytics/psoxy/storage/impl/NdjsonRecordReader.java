package co.worklytics.psoxy.storage.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import org.apache.commons.lang3.StringUtils;
import com.jayway.jsonpath.Configuration;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class NdjsonRecordReader implements RecordReader {

    final Reader reader;
    final Configuration jsonConfiguration;
    
    BufferedReader bufferedReader;

    @Override
    public Object readRecord() throws IOException {
        if (bufferedReader == null) {
            bufferedReader = new BufferedReader(reader);
        }
        
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            line = StringUtils.trimToNull(line);
            if (line != null) {
                return jsonConfiguration.jsonProvider().parse(line);
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
