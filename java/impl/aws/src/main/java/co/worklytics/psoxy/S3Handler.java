package co.worklytics.psoxy;

import co.worklytics.psoxy.aws.DaggerAwsContainer;
import co.worklytics.psoxy.gateway.impl.CommonRequestHandler;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

@Log
public class S3Handler implements com.amazonaws.services.lambda.runtime.RequestHandler<S3Event, String> {


    private static DateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");

    @Inject
    CommonRequestHandler requestHandler;

    @SneakyThrows
    @Override
    public String handleRequest(S3Event s3Event, Context context) {

        //interfaces:
        // - HttpRequestEvent --> HttpResponseEvent

        //q: what's the component?
        // - request handler?? but it's abstract ...
        //    - make it bound with interface, rather than generic? --> prob best approach
        // - objectMapper
        //

        DaggerAwsContainer.create().injectS3Handler(this);

        String response = "200 OK";
        S3EventNotification.S3EventNotificationRecord record = s3Event.getRecords().get(0);

        String importBucket = record.getS3().getBucket().getName();
        String sourceKey = record.getS3().getObject().getUrlDecodedKey();

        String destinationBucket = importBucket.replace("import", "processed");
        String destinationKey = String.format("%s/%s", formatter.format(Date.from(Instant.now())), sourceKey);

        AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
        S3Object s3Object = s3Client.getObject(new GetObjectRequest(importBucket, sourceKey));
        InputStream objectData = s3Object.getObjectContent();

        byte[] pseudonomizedContent = null; //TODO: fileHandler.pseudonomize(objectData)
        InputStream is = new ByteArrayInputStream(pseudonomizedContent);

        ObjectMetadata meta = new ObjectMetadata();
        meta.setContentLength(pseudonomizedContent.length);
        meta.setContentType(s3Object.getObjectMetadata().getContentType());

        System.out.println("Writing to: " + destinationBucket + "/" + destinationKey);
        try {
            s3Client.putObject(destinationBucket, destinationKey, is, meta);
        }
        catch(AmazonServiceException e)
        {
            System.err.println(e.getErrorMessage());
            System.exit(1);
        }

        System.out.println("Successfully pseudonimized " + importBucket + "/"
                + sourceKey + " and uploaded to " + destinationBucket + "/" + destinationKey);

        return response;
    }
}
