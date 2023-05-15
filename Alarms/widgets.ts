import {AlarmWidget, GraphWidget, IAlarm, IWidget, TextWidget} from "aws-cdk-lib/aws-cloudwatch";

const AlarmWidgetWidth = 12;
const AlarmWidgetHeight = 6;
const AlarmTitleWidgetWidth = 24;
const AlarmTitleWidgetHeight = 1;

export function createAlarmWidgets(alarms: IAlarm[])  : IWidget[][]{
    let alarmWidgets: AlarmWidget[] = [];
    alarms.forEach(alarm => {
        alarmWidgets.push(new AlarmWidget({
            title: alarm.alarmName,
            alarm: alarm,
            width: AlarmWidgetWidth,
            height: AlarmWidgetHeight,
        }));
    })
    return [alarmWidgets];
}

/**
 * This function creates Text Widget for the dashboard.
 * @param title - title of widget
 */
export function createTextWidget(title: string) : IWidget[][]{
    let textWidget: TextWidget[] = [];
    textWidget.push(new TextWidget({
        width: AlarmTitleWidgetWidth,
        height: AlarmTitleWidgetHeight,
        markdown: title
    }));
    return [textWidget];
}