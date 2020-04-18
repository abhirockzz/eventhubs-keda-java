package com.abhirockzz;

import com.azure.messaging.eventhubs.*;
import com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore;
import com.azure.messaging.eventhubs.models.ErrorContext;
import com.azure.messaging.eventhubs.models.EventContext;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobContainerClientBuilder;

import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

public class App {
    static String defaultConsumerGroup = "$Default";
    static String storageConnectionStringEnvVarName = "AZURE_STORAGE_CONNECTION_STRING";
    static String storageContainerEnvVarName = "AZURE_STORAGE_CONTAINER";
    static String ehConnectionStringEnvVarName = "EVENTHUB_CONNECTION_STRING";
    static String ehConsumerGroupEnvVarName = "EVENTHUB_CONSUMER_GROUP";

    public static void main(final String[] args) throws Exception {

        final String storageConnectionString = System.getenv(storageConnectionStringEnvVarName);
        if (storageConnectionString == null) {
            throw new Exception("Missing environment variable " + storageConnectionStringEnvVarName);
        }
        System.out.println("storageConnectionString==" + storageConnectionString);

        final String storageContainerName = System.getenv(storageContainerEnvVarName);
        if (storageContainerName == null) {
            throw new Exception("Missing environment variable " + storageContainerEnvVarName);
        }
        System.out.println("storageContainerName==" + storageContainerName);

        final String eventHubConnectionString = System.getenv(ehConnectionStringEnvVarName);
        if (eventHubConnectionString == null) {
            throw new Exception("Missing environment variable " + ehConnectionStringEnvVarName);
        }
        System.out.println("eventHubConnectionString==" + eventHubConnectionString);

        String consumerGroupName = System.getenv(ehConsumerGroupEnvVarName);
        if (consumerGroupName == null) {
            consumerGroupName = defaultConsumerGroup;
        }
        System.out.println("consumerGroupName==" + consumerGroupName);

        // function to process events
        final Consumer<EventContext> processEvent = eventContext -> {
            System.out.print("Received event: ");
            // print the body of the event
            System.out.println("event body: " + eventContext.getEventData().getBodyAsString());
            System.out.println("partition ID: " + eventContext.getPartitionContext().getPartitionId());
            System.out.println("offset: " + eventContext.getEventData().getOffset());
            // sleep on purpose
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            eventContext.updateCheckpoint();
        };

        // function to process errors
        final Consumer<ErrorContext> processError = errorContext -> {
            // print the error message
            System.out.println(errorContext.getThrowable().getMessage());
        };

        final BlobContainerAsyncClient storageClient = new BlobContainerClientBuilder()
                .connectionString(storageConnectionString).containerName(storageContainerName).buildAsyncClient();

        final EventProcessorClient eventProcessorClient = new EventProcessorClientBuilder()
                .connectionString(eventHubConnectionString).processEvent(processEvent).processError(processError)
                .consumerGroup(consumerGroupName)
                // .consumerGroup(EventHubClientBuilder.DEFAULT_CONSUMER_GROUP_NAME)
                .checkpointStore(new BlobCheckpointStore(storageClient))
                .processPartitionInitialization(
                        ic -> System.out.println("init partition: " + ic.getPartitionContext().getPartitionId()))
                .processPartitionClose(cc -> System.out.println("closed partition: "
                        + cc.getPartitionContext().getPartitionId() + " due to " + cc.getCloseReason().toString()))
                .buildEventProcessorClient();

        eventProcessorClient.start();
        System.out.println("Started event processor");

        CountDownLatch cl = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

            @Override
            public void run() {
                System.out.println("Stopped... ");
                cl.countDown();
            }
        }));

        cl.await();

        System.out.println("Stopping event processor");
        eventProcessorClient.stop();
        System.out.println("Event processor stopped.");

        System.out.println("Exiting process");
    }

}
