import {Construct} from "constructs";
import {Stage} from "../configurations/stages/stages";
import {createLowSevComponentAlarm} from "../monitoring/createAlarms";
import {Dashboard, IWidget} from "aws-cdk-lib/aws-cloudwatch";
import {ActionType, Severity, SIMTicketAlarmAction} from "@amzn/motecdk/mote-ecr";

export class MonitoringComponent extends Construct{

    constructor(scope: Construct, idSuffix: string, stageProps :Stage) {
        super(scope, `MeridianServiceMonitor${idSuffix}-${stageProps.stageName}`);

        let lowSevAlarmDashboard : IWidget[][] = [];

        const lowSevMainAlarm = createLowSevComponentAlarm(this, stageProps, lowSevAlarmDashboard, idSuffix)
        lowSevMainAlarm.addAlarmAction(this.createTicketAction(Severity.SEV3))

        this.createDashboard(scope, `MeridianService-LowSev-Dashboard-${stageProps.stageName}${idSuffix}`, lowSevAlarmDashboard);
    }

    createTicketAction(severity: Severity) {
        return new SIMTicketAlarmAction({
            actionType : ActionType.TICKET,
            severity : severity,
            category : "External Payments",
            type : "PaymentsProduct",
            item : "Merchant Risk",
        });
    }

    createDashboard(scope: Construct, dashboardName: string, widgets: IWidget[][]){
        const DashboardStartTimeRange = "-P1W";
        return new Dashboard(scope, dashboardName, {
            dashboardName: dashboardName,
            start : DashboardStartTimeRange,
            widgets: widgets,
        })
    }
}