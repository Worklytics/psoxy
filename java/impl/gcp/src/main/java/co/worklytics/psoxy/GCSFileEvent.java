package co.worklytics.psoxy;

import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import lombok.Data;
import lombok.extern.java.Log;


import javax.inject.Singleton;
import java.io.*;

//TODO: rename this??
@Singleton
@Log
public class GCSFileEvent implements BackgroundFunction<GCSFileEvent.GcsEvent> {


    volatile GcpContainer container;

    @Override
    public void accept(GcsEvent gcsEvent, Context context) throws Exception {
       injectDependenciesIfNeeded();

       container.gcsFileEventHandler().process(gcsEvent, context);
    }


    /**
     * See https://github.com/GoogleCloudPlatform/java-docs-samples/tree/460f5cffd9f8df09146947515458f336881e29d8/functions/helloworld/hello-gcs/src/main/java/functions
     * and https://cloud.google.com/functions/docs/writing/background#cloud-storage-example
     *
     * Original code include more fields that are not currently required
     */
    @Data
    public static class GcsEvent {
        // Cloud Functions uses GSON to populate this object.
        // Field types/names are specified by Cloud Functions
        // Changing them may break your code!
        private String bucket;
        private String name;
        private String metageneration;
    }


    void injectDependenciesIfNeeded() {
        if (container == null) {
            synchronized (this) {
                if (container == null) {
                    container = DaggerGcpContainer.create();
                }
            }
        }
    }
}
