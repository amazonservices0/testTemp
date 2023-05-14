package com.amazon.amazonpaymerchanturl.adapter;

import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLNonRetryableException;
import com.amazon.amazonpaymerchanturl.exceptions.AmazonPayMerchantURLRetryableException;
import com.amazonaws.AmazonServiceException.ErrorType;
import com.amazonaws.services.stepfunctions.AWSStepFunctions;
import com.amazonaws.services.stepfunctions.model.AWSStepFunctionsException;
import com.amazonaws.services.stepfunctions.model.DescribeExecutionRequest;
import com.amazonaws.services.stepfunctions.model.DescribeExecutionResult;
import com.amazonaws.services.stepfunctions.model.ListExecutionsRequest;
import com.amazonaws.services.stepfunctions.model.ListExecutionsResult;
import com.amazonaws.services.stepfunctions.model.SendTaskSuccessRequest;
import com.amazonaws.services.stepfunctions.model.StartExecutionRequest;
import com.amazonaws.services.stepfunctions.model.StartExecutionResult;

import javax.inject.Singleton;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

/**
 * StepFunctionAdapter for starting/resuming Step Functions.
 */
@Log4j2
@Singleton
@RequiredArgsConstructor
public class StepFunctionAdapter {
    private final AWSStepFunctions stepFunction;

    private static final String USED_TASK_TOKEN_ERROR_MESSAGE = "Provided task does not exist anymore";

    /**
     * startWorkflow method to start new execution of StepFunction.
     *
     * @param stateMachineArn ARN of StepFunction to be started
     * @param payload         Payload to pass to StepFunction
     * @return String executionId
     */
    public String startWorkflow(@NonNull final String stateMachineArn, @NonNull final String payload) {
        final StartExecutionRequest request = new StartExecutionRequest()
                .withStateMachineArn(stateMachineArn)
                .withInput(payload);

        return startExecution(request);
    }

    /**
     * startWorkflow method to start new execution of StepFunction.
     *
     * @param stateMachineArn ARN of StepFunction to be started
     * @param payload         Payload to pass to StepFunction
     * @param name            Name of the stepFunction workflow
     * @return String executionId
     */
    public String startWorkflow(@NonNull final String stateMachineArn, @NonNull final String payload,
                                @NonNull final String name) {
        final StartExecutionRequest request = new StartExecutionRequest()
                .withStateMachineArn(stateMachineArn)
                .withInput(payload)
                .withName(name);

        return startExecution(request);
    }

    private String startExecution(final StartExecutionRequest startExecutionRequest) {
        try {
            final StartExecutionResult startExecutionResult = stepFunction.startExecution(startExecutionRequest);

            final String executionId = startExecutionResult.getExecutionArn();
            log.info("Step function [{}] started successfully", executionId);
            return executionId;
        } catch (final AWSStepFunctionsException e) {
            if (ErrorType.Client.equals(e.getErrorType())) {
                throw new AmazonPayMerchantURLNonRetryableException("Invalid input passed to StepFunction", e);
            }
            throw new AmazonPayMerchantURLRetryableException("Exception while executing StepFunction", e);
        }
    }

    /**
     * resumeWorkflow method to resume execution of StepFunction with the taskToken.
     *
     * @param taskToken TaskToken to resume the workflow
     * @param payload   Payload output to pass to StepFunction task
     * @return boolean True if the StepFunction resumed, false if token is not valid
     */
    public boolean resumeWorkflow(@NonNull final String taskToken, @NonNull final String payload) {
        final SendTaskSuccessRequest request = new SendTaskSuccessRequest()
                .withTaskToken(taskToken)
                .withOutput(payload);
        try {
            stepFunction.sendTaskSuccess(request);
            log.info("Step function resumed successfully for taskToken [{}] ", taskToken);
            return true;
        } catch (final AWSStepFunctionsException e) {
            if (ErrorType.Client.equals(e.getErrorType())) {
                // Need to check error message for already used token since the exception thrown for already used token
                // is TaskTimedOutException with Client ErrorType.
                // So to distinguish from other client exceptions, we check for error message
                if (StringUtils.contains(e.getErrorMessage(), USED_TASK_TOKEN_ERROR_MESSAGE)) {
                    log.info("TaskToken [{}] does not exist anymore", taskToken);
                    return false;
                }
                throw new AmazonPayMerchantURLNonRetryableException("Invalid input passed to StepFunction", e);
            }
            throw new AmazonPayMerchantURLRetryableException("Exception while resuming StepFunction", e);
        }
    }

    /**
     * getWorkflowStatus method to get status of a given execution of StepFunction.
     *
     * @param workflowId workflowId whose status needs to be checked
     * @return String Status of the workflowId passed
     */
    public String getWorkflowStatus(@NonNull final String workflowId) {
        return describeExecutionResult(workflowId).getStatus();
    }

    /**
     * getWorkflowStatus method to get input of a given execution of StepFunction.
     *
     * @param workflowId workflowId whose status needs to be checked
     * @return String input of the workflowId passed
     */
    public String getWorkflowInput(@NonNull final String workflowId) {
        return describeExecutionResult(workflowId).getInput();
    }

    /**
     * getDescribeExecutionResult method get DescribeExecutionResult of StepFunction.
     *
     * @param workflowId workflowId whose status needs to be checked
     * @return describeExecutionResult of the workflowId passed
     */
    public DescribeExecutionResult describeExecutionResult(@NonNull final String workflowId) {
        final DescribeExecutionRequest request = new DescribeExecutionRequest()
                .withExecutionArn(workflowId);
        try {
            final DescribeExecutionResult describeExecutionResult = stepFunction.describeExecution(request);
            log.info("Step function status for workflowId [{}] is {}", workflowId, describeExecutionResult.getStatus());
            return describeExecutionResult;
        } catch (final AWSStepFunctionsException e) {
            if (ErrorType.Client.equals(e.getErrorType())) {
                throw new AmazonPayMerchantURLNonRetryableException("Invalid workflowId passed: " + workflowId, e);
            }
            throw new AmazonPayMerchantURLRetryableException("Exception while getting workflow status for: "
                    + workflowId, e);
        }
    }

    /**
     * This method returns all the executions of given status.
     *
     * @param stateMachineArn stateMachineArn
     * @param workflowStatus workflowStatus
     * @param maxResults maxResults
     * @param nextToken nextToken
     * @return List of executionListItem with workflowStatus which passed.
     */
    public ListExecutionsResult getExecutionsForGivenStatus(@NonNull final String stateMachineArn,
                                                            @NonNull final String workflowStatus,
                                                            final int maxResults, final String nextToken) {
        final ListExecutionsRequest request = new ListExecutionsRequest()
                .withStateMachineArn(stateMachineArn)
                .withStatusFilter(workflowStatus)
                .withMaxResults(maxResults);

        if (StringUtils.isNotBlank(nextToken)) {
            request.withNextToken(nextToken);
        }

        try {
            final ListExecutionsResult listExecutionsResult = stepFunction.listExecutions(request);
            log.info("List of step function executionItem with workflowStatus [{}] is {}", workflowStatus,
                    listExecutionsResult);
            return listExecutionsResult;
        } catch (final AWSStepFunctionsException e) {
            if (ErrorType.Client.equals(e.getErrorType())) {
                throw new AmazonPayMerchantURLNonRetryableException("Invalid workflowStatus passed: "
                        + workflowStatus, e);
            }
            throw new AmazonPayMerchantURLRetryableException("Exception while getting ExecutionListItems with "
                    + "workflow status: " + workflowStatus, e);
        }
    }

}
