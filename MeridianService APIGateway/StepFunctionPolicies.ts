import {Construct} from "constructs";
import {SecureIManagedPolicy} from "@amzn/motecdk/lib/mote-iam/lib/policy";
import {SecureManagedPolicy, SecurePolicyStatement} from "@amzn/motecdk/mote-iam";
import {Effect} from "aws-cdk-lib/aws-iam";

export class StepFunctionPolicies {
    public static addStepFunctionStartExecutionManagedPolicy(scope: Construct, id: string, stateMachineArn:string, managedPolicies: SecureIManagedPolicy[]) {
        const startExecutionManagedPolicy = new SecureManagedPolicy(scope, id + 'policy', {
            statements: [
                new SecurePolicyStatement({
                    effect: Effect.ALLOW,
                    actions: [
                        'states:StartExecution',
                    ],
                    resources: [stateMachineArn],
                }),
            ],
        });
        managedPolicies.push(startExecutionManagedPolicy)
    }
}