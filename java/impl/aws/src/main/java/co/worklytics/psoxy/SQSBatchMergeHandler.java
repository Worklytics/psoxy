package co.worklytics.psoxy;

import co.worklytics.psoxy.aws.AwsContainer;
import co.worklytics.psoxy.aws.SQSOutput;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.BatchMergeHandler;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

/**
 * entry point for AWS Lambda function that processes SQS events (merging multiple messages into a single batch write to output)
 */
public class SQSBatchMergeHandler implements com.amazonaws.services.lambda.runtime.RequestHandler<com.amazonaws.services.lambda.runtime.events.SQSEvent, Void> {

    /**
     * Static initialization allows reuse in containers
     * {@link "https://aws.amazon.com/premiumsupport/knowledge-center/lambda-improve-java-function-performance/"}
     */
    static AwsContainer awsContainer;

    static BatchMergeHandler batchMergeHandler;

    static {
        staticInit();
    }

    private static void staticInit() {
        awsContainer = DaggerAwsContainer.create();
        batchMergeHandler = awsContainer.batchMergeHandler();
    }

    @Override
    public Void handleRequest(com.amazonaws.services.lambda.runtime.events.SQSEvent sqsEvent, com.amazonaws.services.lambda.runtime.Context context) {

        Stream<ProcessedContent> processedContentStream = sqsEvent.getRecords().stream().map(r -> ProcessedContent.builder()
            .contentType(r.getMessageAttributes().get(SQSOutput.MessageAttributes.CONTENT_TYPE).getStringValue())
            .contentEncoding(r.getMessageAttributes().get(SQSOutput.MessageAttributes.CONTENT_ENCODING).getStringValue())
            .content(r.getBody().getBytes(StandardCharsets.UTF_8))
            .build());

        batchMergeHandler.handleBatch(processedContentStream);

        return null; // Placeholder return value
    }
}
