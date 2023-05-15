import {Stage} from "../../configurations/stages/stages";
import {MetricProps} from "../createAlarms";
import {getResourceName} from "../../constants";
import {ComparisonOperator, Metric, Statistic, TreatMissingData } from "aws-cdk-lib/aws-cloudwatch";
import { GetBatchMerchantRiskApi, GetMerchantRiskApi, OnboardBatchMerchantRiskApi, UpdateMerchantApi } from "../../constants/apiGateway";
import { Duration } from "aws-cdk-lib";

const Metric_5XXError = "5XXError";
const Metric_4XXError = "4XXError";
const Metric_Latency = "Latency";

export function getApiLowSevAlarmProps(stageProps: Stage, idSuffix: string) {
    return {
        //getMetricProps(metricName, statistic, periodsInSec, evalPeriods, datapointsToAlarm, threshold,  stageProps, apiGatewayName, , idSuffix)
        getBatchMerchantApi5xxProps     : getMetricProps(Metric_5XXError, Statistic.SUM, 60, 5, 3, 1, stageProps, GetBatchMerchantRiskApi, idSuffix),
        onboardBatchMerchantApi5xxProps : getMetricProps(Metric_5XXError, Statistic.SUM, 60, 5, 3, 1, stageProps, OnboardBatchMerchantRiskApi, idSuffix),
        updateBatchMerchantApi5xxProps  : getMetricProps(Metric_5XXError, Statistic.SUM, 60, 5, 3, 1, stageProps, UpdateMerchantApi, idSuffix),
        getMerchantRiskApi5xxProps      : getMetricProps(Metric_5XXError, Statistic.SUM, 60, 5, 3, 1, stageProps, GetMerchantRiskApi, idSuffix),

        getBatchMerchantApi4xxProps     : getMetricProps(Metric_4XXError, Statistic.SUM,  60, 5, 3, 1, stageProps, GetBatchMerchantRiskApi, idSuffix),
        onboardBatchMerchantApi4xxProps : getMetricProps(Metric_4XXError, Statistic.SUM,  60, 5, 3, 1, stageProps, OnboardBatchMerchantRiskApi, idSuffix),
        updateBatchMerchantApi4xxProps  : getMetricProps(Metric_4XXError, Statistic.SUM,  60, 5, 3, 1, stageProps, UpdateMerchantApi, idSuffix),
        getMerchantRiskApi4xxProps      : getMetricProps(Metric_4XXError, Statistic.SUM,  60, 5, 3, 1, stageProps, GetMerchantRiskApi, idSuffix),

        getBatchMerchantApiLatencyProps : getMetricProps(Metric_Latency, Statistic.MAXIMUM,  60, 5, 3, 200, stageProps, GetBatchMerchantRiskApi, idSuffix),
        onboardBatchMerchantApiLatencyProps : getMetricProps(Metric_Latency, Statistic.MAXIMUM,  60, 5, 3, 200, stageProps, OnboardBatchMerchantRiskApi, idSuffix),
        updateBatchMerchantApiLatencyProps : getMetricProps(Metric_Latency, Statistic.MAXIMUM,  60, 5, 3, 200, stageProps, UpdateMerchantApi, idSuffix),
        getMerchantRiskApiLatencyProps : getMetricProps(Metric_Latency, Statistic.MAXIMUM,  60, 5, 3, 8000, stageProps, GetMerchantRiskApi, idSuffix)
    }
}

function getMetricProps(metricName: string, statistic: Statistic, periodsInSec: number, evalPeriods: number,
                        datapointsToAlarm: number, threshold: number,  stageProps: Stage, apiGatewayName : string, idSuffix: string) : MetricProps {

    const resourceName = getResourceName(apiGatewayName, idSuffix)
    return {
        name : resourceName,
        metric : new Metric(getApiGatewayCommonProps(metricName, statistic, periodsInSec, stageProps, resourceName)),
        evalulationPeriods : evalPeriods,
        datapointsToAlarm : datapointsToAlarm,
        threshold : threshold,
        comparsionOperator : ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD,
        treatMissingData : TreatMissingData.IGNORE
    }
}

function getApiGatewayCommonProps(metricName: string, statistic: Statistic, periodInSeconds: number, stageProps: Stage, apiGatewayName: string) {
    return {
        namespace: "AWS/ApiGateway",
        dimensions: {
            ApiName: apiGatewayName,
        },
        period: Duration.seconds(periodInSeconds),
        metricName: metricName,
        account: stageProps.accountId,
        region: stageProps.region,
        statistic: statistic
    }
}