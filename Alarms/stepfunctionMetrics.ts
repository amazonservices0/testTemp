import { Duration } from "aws-cdk-lib";
import {ComparisonOperator, Metric, Statistic, TreatMissingData } from "aws-cdk-lib/aws-cloudwatch";
import {Stage} from "../../configurations/stages/stages";
import { getResourceName } from "../../constants";
import {
    GetBatchMerchantWorkflow,
    OnboardMerchantWorkflow,
    RefreshMerchantDataWorkflow,
    UpdateMerchantWorkflow
} from "../../constants/stepfunction";
import { MetricProps } from "../createAlarms";

const Metric_ExecutionFailed= "ExecutionsFailed";
const Metric_ExecutionTimedOut= "ExecutionTimedOut";

export function getStepFunctionLowSevAlarmProps(stageProps: Stage, idSuffix: string) {
    return {
        //getMetricProps(metricName, statistic, periodsInSec, evalPeriods, datapointsToAlarm, threshold,  stageProps, stepFunctionName, idSuffix)
        getBatchMerchantSFExecutionFailedProps  : getMetricProps(Metric_ExecutionFailed, Statistic.SUM, 60, 5, 1, 1, stageProps, GetBatchMerchantWorkflow, idSuffix),
        onboardBatchSFExecutionFailedProps      : getMetricProps(Metric_ExecutionFailed, Statistic.SUM, 60, 5, 1, 1, stageProps, OnboardMerchantWorkflow, idSuffix),
        updateBatchSFExecutionFailedProps       : getMetricProps(Metric_ExecutionFailed, Statistic.SUM, 60, 5, 1, 1, stageProps, UpdateMerchantWorkflow, idSuffix),
        refreshMerchantSFExecutionFailedProps   : getMetricProps(Metric_ExecutionFailed, Statistic.SUM, 60, 5, 1, 1, stageProps, RefreshMerchantDataWorkflow, idSuffix),

        getBatchMerchantSFExecutionTimedOutProps: getMetricProps(Metric_ExecutionTimedOut, Statistic.SUM, 60, 5, 1, 1, stageProps, GetBatchMerchantWorkflow, idSuffix),
        onboardBatchSFExecutionTimedOutProps    : getMetricProps(Metric_ExecutionTimedOut, Statistic.SUM, 60, 5, 1, 1, stageProps, OnboardMerchantWorkflow, idSuffix),
        updateBatchSFExecutionTimedOutProps     : getMetricProps(Metric_ExecutionTimedOut, Statistic.SUM, 60, 5, 1, 1, stageProps, UpdateMerchantWorkflow, idSuffix),
        refreshMerchantSFExecutionTimedOutProps : getMetricProps(Metric_ExecutionTimedOut, Statistic.SUM, 60, 5, 1, 1, stageProps, RefreshMerchantDataWorkflow, idSuffix),
    }
}

function getMetricProps(metricName: string, statistic: Statistic, periodsInSec: number, evalPeriods: number,
                        datapointsToAlarm: number, threshold: number,  stageProps: Stage, stepFunctionName : string, idSuffix: string) : MetricProps {
    const resourceName = getResourceName(stepFunctionName, idSuffix);
    return {
        name: resourceName,
        metric : new Metric(getStepfunctionCommonProps(metricName, statistic, periodsInSec, stageProps, resourceName)),
        evalulationPeriods : evalPeriods,
        datapointsToAlarm : datapointsToAlarm,
        threshold : threshold,
        comparsionOperator : ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
        treatMissingData : TreatMissingData.IGNORE
    }
}

function getStepfunctionCommonProps(metricName: string, statistic: Statistic, periodInSeconds: number, stageProps: Stage, workflowName: string) {
    return {
        namespace: "AWS/States",
        dimensions: {
            StateMachineArn: `arn:aws:states:${stageProps.region}:${stageProps.accountId}:stateMachine:${workflowName}`,
        },
        metricName: metricName,
        period: Duration.seconds(periodInSeconds),
        account: stageProps.accountId,
        region: stageProps.region,
        statistic: statistic
    }
}