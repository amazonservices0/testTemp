import {Construct} from "constructs";
import {Stage} from "../configurations/stages/stages";
import {
    Alarm, AlarmRule, ComparisonOperator, CompositeAlarm, IAlarm, IWidget, Metric, TreatMissingData
} from "aws-cdk-lib/aws-cloudwatch";
import {SecureMetric} from "@amzn/motecdk/mote-cloudwatch";
import {getApiLowSevAlarmProps} from "./metrics/apiGatewayMetrics";
import {getStepFunctionLowSevAlarmProps} from "./metrics/stepfunctionMetrics";
import {createAlarmWidgets, createTextWidget } from "./widgets";

export interface MetricProps {
    name: string,
    metric : Metric,
    evalulationPeriods : number,
    datapointsToAlarm : number,
    threshold : number,
    comparsionOperator : ComparisonOperator,
    treatMissingData : TreatMissingData,
}

//Method that have the Alarm resources in a Hierarchical structure(Component and Resource based Structure)
export function createLowSevComponentAlarm(scope: Construct, stageProps: Stage, dashboardWidgets: IWidget[][], idSuffix: string){
    const apiLowSevAlarmProps = getApiLowSevAlarmProps(stageProps, idSuffix);
    const stepFunctionLowSevAlarmProps = getStepFunctionLowSevAlarmProps(stageProps, idSuffix);

    const component = {
        getBatchMerchantComponentAlarms : {
            apiGatewayAlarms: [
                apiLowSevAlarmProps.getBatchMerchantApi5xxProps,
                apiLowSevAlarmProps.getBatchMerchantApi4xxProps,
                apiLowSevAlarmProps.getBatchMerchantApiLatencyProps,
            ],
            stepFunctionAlarms: [
                stepFunctionLowSevAlarmProps.getBatchMerchantSFExecutionFailedProps,
                stepFunctionLowSevAlarmProps.getBatchMerchantSFExecutionTimedOutProps
            ]
        },
        onboardBatchMerchantComponentAlarm : {
            apiGatewayAlarm: [
                apiLowSevAlarmProps.onboardBatchMerchantApi5xxProps,
                apiLowSevAlarmProps.onboardBatchMerchantApi4xxProps,
                apiLowSevAlarmProps.onboardBatchMerchantApiLatencyProps,
            ],
            stepFunctionAlarm: [
                stepFunctionLowSevAlarmProps.onboardBatchSFExecutionFailedProps,
                stepFunctionLowSevAlarmProps.onboardBatchSFExecutionTimedOutProps
            ]
        },
        updateBatchMerchantComponentAlarm : {
            apiGatewayAlarm: [
                apiLowSevAlarmProps.updateBatchMerchantApi5xxProps,
                apiLowSevAlarmProps.updateBatchMerchantApi4xxProps,
                apiLowSevAlarmProps.updateBatchMerchantApiLatencyProps,
            ],
            stepFunctionAlarm: [
                stepFunctionLowSevAlarmProps.updateBatchSFExecutionFailedProps,
                stepFunctionLowSevAlarmProps.updateBatchSFExecutionTimedOutProps
            ]
        },
        getMerchantRiskComponentAlarm : {
            apiGatewayAlarm: [
                apiLowSevAlarmProps.getMerchantRiskApi5xxProps,
                apiLowSevAlarmProps.getMerchantRiskApi4xxProps,
                apiLowSevAlarmProps.getMerchantRiskApiLatencyProps,
            ]
        },
        refreshMerchantComponentAlarm : {
            stepFunctionAlarm: [
                stepFunctionLowSevAlarmProps.refreshMerchantSFExecutionFailedProps,
                stepFunctionLowSevAlarmProps.refreshMerchantSFExecutionTimedOutProps
            ]
        }
    }

    return createComponentAlarm(scope, stageProps, "Low-Sev", component, dashboardWidgets, idSuffix);
}

//Creates the Main Alarms and Sub Alarms similar to the Structure mentioned in createLowSevComponentAlarm() method
function createComponentAlarm(scope: Construct, stageProps: Stage, severity: string, component : any, dashboardWidgets: IWidget[][], idSuffix: string) {
    let aggregateComponentAlarms: IAlarm[] = [];

    for (const componentName in component) {
        const resourceTypes = component[componentName];
        dashboardWidgets.push(...createTextWidget(`# ${componentName}`))

        let aggregateResourceAlarms: IAlarm[] = [];

        for (const resourceType in resourceTypes) {
            const serviceMetrics: MetricProps[] = resourceTypes[resourceType];
            const resourceTypeAlarms = serviceMetrics.map(serviceMetric => generateAlarm(scope, stageProps, serviceMetric, severity));
            aggregateResourceAlarms.push(createCompositeAlarm(scope, `${componentName}-${resourceType}-${severity}`, ...resourceTypeAlarms))

            //Add Alarm widgets under resource type for each component
            dashboardWidgets.push(...createTextWidget(`## ${componentName}-${resourceType}`))
            dashboardWidgets.push(...createAlarmWidgets(resourceTypeAlarms))
        }
        aggregateComponentAlarms.push(createCompositeAlarm(scope, `${componentName}-${severity}`, ...aggregateResourceAlarms))
    }

    return createCompositeAlarm(scope, `Aggregate${idSuffix}-${severity}`, ...aggregateComponentAlarms);
}

// This method creates an Alarm for the given metrics
function generateAlarm(scope : Construct, stageProps : Stage, metricProps : MetricProps, severity: string) : IAlarm{

    const alarmId = `${metricProps.name}-${metricProps.metric.metricName}-${stageProps.stageName}-${severity}`
    return new Alarm(scope, alarmId, {
        alarmName: alarmId,
        alarmDescription: alarmId,
        evaluationPeriods: metricProps.evalulationPeriods,
        datapointsToAlarm: metricProps.datapointsToAlarm,
        threshold: metricProps.threshold,
        comparisonOperator: metricProps.comparsionOperator,
        treatMissingData: metricProps.treatMissingData,
        metric: new SecureMetric(metricProps.metric)
    });
}

//This method creates the  Composite Alarm with the given list of Sub Alarms
export function createCompositeAlarm(scope : Construct, AlarmType : string, ...alarms : IAlarm[]) {
    return new CompositeAlarm(scope, `MeridianService-${AlarmType}`, {
        alarmDescription: `Composite alarm for Meridian Service`,
        compositeAlarmName: `MeridianService-${AlarmType}-Composite-Alarm`,
        alarmRule: AlarmRule.anyOf(...alarms),
        actionsEnabled: false
    });
}