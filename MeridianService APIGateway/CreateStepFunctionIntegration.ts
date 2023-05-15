import { AwsIntegration } from "aws-cdk-lib/aws-apigateway";
import {Construct} from "constructs";
import { integrationResponse } from "../../../constants/apiGateway";
import {ApiProps} from "../../createApi";
import {Role} from "../../createRole";
import {getRoleName} from "../../../constants/role";
import {SecureRole} from "@amzn/motecdk/mote-iam";
import { HttpMethod } from "aws-cdk-lib/aws-events";
import {getApiGatewayAsyncStepFunctionIntegrationPolicies} from "../../../helper/apigateway/ApiGatewayIntegrationHelper";

export class StepFunctionIntegration extends Construct {

    private readonly scope: Construct;
    private readonly id: string;
    private readonly stateMachineArn: string;
    private readonly AsyncStepFunctionIntegration: AwsIntegration;

    constructor(scope: Construct, id: string, stateMachineArn: string) {
        super(scope, id+'StepfunctionIntegration');
        this.id = id;
        this.scope = scope;
        this.stateMachineArn = stateMachineArn,
        this.AsyncStepFunctionIntegration = this.createAsyncStepFunctionIntegration();
    }

    private createAsyncStepFunctionIntegration() {

        const sfIntegrationApiGwRole = new Role(this, getRoleName(this.id, ""),
            getApiGatewayAsyncStepFunctionIntegrationPolicies(this, this.id, this.stateMachineArn))
            .createApiGatewayRole();

        return new AwsIntegration({
            service: 'states',
            action: 'StartExecution',
            integrationHttpMethod: HttpMethod.POST,
            options: {
                credentialsRole: sfIntegrationApiGwRole,
                requestTemplates: {
                    'application/json': JSON.stringify({
                        input: "$util.escapeJavaScript($input.json('$'))",
                        stateMachineArn: this.stateMachineArn,
                    }),
                },
                integrationResponses: integrationResponse
            }
        });
    }

    public getAyncStepFunctionIntegration() :AwsIntegration{
        return this.AsyncStepFunctionIntegration
    }
}