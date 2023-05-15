import {
    Choice,
    Condition, Fail,
    IChainable,
    IntegrationPattern,
    JsonPath, Pass,
    Succeed,
    TaskInput
} from "aws-cdk-lib/aws-stepfunctions";
import {Construct} from "constructs";
import {StepFunction} from "../../constructs/createStepfunction";
import {SecureFunction} from "@amzn/motecdk/mote-lambda";
import {LambdaInvoke, StepFunctionsStartExecution} from "aws-cdk-lib/aws-stepfunctions-tasks";
import {DefaultRetryConfig} from "../../constants/stepfunction";

export interface RefreshMerchantDataSfnProps {

    readonly updateMerchantDataSfn: StepFunction,
    readonly onboardMerchantDataSfn: StepFunction,
    readonly refreshMerchantDriverLambda: SecureFunction
}
export function createDefinition (scope: Construct, idSuffix: string, props: RefreshMerchantDataSfnProps): IChainable{

    const refreshComponentRequester = "REFRESH_DATA_WORKFLOW";
    const succeedState = new Succeed(scope, `RefreshMerchantDataSuccess-State-${idSuffix}`);
    const failState = new Fail(scope, `RefreshMerchantDataFail-State-${idSuffix}`);

    const refreshMerchantDriverJob = new LambdaInvoke(scope, `RefreshMerchantDriver-Job-${idSuffix}`, {
        lambdaFunction: props.refreshMerchantDriverLambda,
        outputPath: "$.Payload"
    }).addRetry(DefaultRetryConfig);

    const handleRefreshJobFailure = new Pass(scope, `RefreshMerchantDataErrorPass-State-${idSuffix}`, {
        parameters: {
            "inputFile.$" : "$.inputFile",
            "failureFile.$" : "$.failureFile",
            "marketplaceIdMerchantIdsMap.$" : "$.jobResult.failedMarketplaceIdMerchantIdsMap"
        }
    }).next(refreshMerchantDriverJob);

    const RefreshMerchantDataJobErrorConfig = {
        errors: ["States.ALL"],
        resultPath: JsonPath.DISCARD
    };

    const updateMerchantDataJob = new StepFunctionsStartExecution(scope, `UpdateMerchantData-Job-${idSuffix}`, {
        integrationPattern: IntegrationPattern.RUN_JOB,
        stateMachine: props.updateMerchantDataSfn.getStateMachine(),
        input: TaskInput.fromObject({
            marketplaceIdMerchantIdsMap: JsonPath.stringAt("$.marketplaceIdMerchantIdsMap"),
            requester: refreshComponentRequester,
        }),
        resultSelector: {
            "failedMarketplaceIdMerchantIdsMap.$":"$.Output.failedMarketplaceIdMerchantIdsMap"
        },
        resultPath: "$.jobResult"
    }).addCatch(handleRefreshJobFailure, RefreshMerchantDataJobErrorConfig);

    const onboardMerchantDataJob = new StepFunctionsStartExecution(scope, `OnboardMerchantData-Job-${idSuffix}`, {
        integrationPattern: IntegrationPattern.RUN_JOB,
        stateMachine: props.onboardMerchantDataSfn.getStateMachine(),
        input: TaskInput.fromObject({
            marketplaceIdMerchantIdsMap: JsonPath.stringAt("$.marketplaceIdMerchantIdsMap"),
            requester: refreshComponentRequester,
        }),
        resultSelector: {
            "failedMarketplaceIdMerchantIdsMap.$":"$.Output.failedMarketplaceIdMerchantIdsMap"
        },
        resultPath: "$.jobResult"
    }).addCatch(handleRefreshJobFailure, RefreshMerchantDataJobErrorConfig);


    const merchantDataJobOutputConversion = new Pass(scope, `merchantDataJobOutputConversionPass-State-${idSuffix}`, {
        parameters: {
            "inputFile.$" : "$.inputFile",
            "failureFile.$" : "$.failureFile",
            "failedMarketplaceIdMerchantIdsMap.$" : "$.jobResult.failedMarketplaceIdMerchantIdsMap",
            "status.$" : "$.status",
            "workflowType.$" : "$.workflowType",
        }
    });

    const inProgressCondition = Condition.or(
        Condition.stringEquals(JsonPath.stringAt("$.status"), "IN_PROGRESS"),
        Condition.stringEquals(JsonPath.stringAt("$.status"), "IN_PROGRESS_WITH_PARTIAL_FAILURE")
    );

    const decideDriverPathJob = new Choice(scope, `DecideDriverPath-Job-${idSuffix}`)
        .when(Condition.isNotPresent(JsonPath.stringAt("$.status")), failState)
        .when(Condition.stringEquals(JsonPath.stringAt("$.status"), "SUCCESS"), succeedState)
        .when(Condition.and(inProgressCondition, Condition.stringEquals(JsonPath.stringAt("$.workflowType"), "UPDATE")), updateMerchantDataJob)
        .when(Condition.and(inProgressCondition, Condition.stringEquals(JsonPath.stringAt("$.workflowType"), "ONBOARD")), onboardMerchantDataJob)
        .otherwise(failState);


    refreshMerchantDriverJob.next(decideDriverPathJob);
    updateMerchantDataJob.next(merchantDataJobOutputConversion)
    onboardMerchantDataJob.next(merchantDataJobOutputConversion)
    merchantDataJobOutputConversion.next(refreshMerchantDriverJob);

    return refreshMerchantDriverJob;
}