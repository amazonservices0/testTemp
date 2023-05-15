import {ApiIntegrationType, integrationResponse} from "../../constants/apiGateway";
import {AwsIntegration} from "aws-cdk-lib/aws-apigateway";
import {Construct} from "constructs";
import {ApiProps} from "../../constructs/createApi";
import {ManagedPolicy, ServicePrincipal} from "aws-cdk-lib/aws-iam";
import {getRoleName} from "../../constants/role";
import {SecureIManagedPolicy} from "@amzn/motecdk/lib/mote-iam/lib/policy";
import {StepFunctionPolicies} from "../../constructs/policies/StepFunctionPolicies";
import { Role } from "../../constructs/createRole";

export function getApiGatewayAsyncStepFunctionIntegrationPolicies(scope:Construct, id: string, stepFunctionArn: string) :SecureIManagedPolicy[] {
    const apiGatewayAsyncStepFunctionIntegrationPolicies: SecureIManagedPolicy[] = [];
    StepFunctionPolicies.addStepFunctionStartExecutionManagedPolicy(scope, id,
        stepFunctionArn,
        apiGatewayAsyncStepFunctionIntegrationPolicies);
    return apiGatewayAsyncStepFunctionIntegrationPolicies;
}
