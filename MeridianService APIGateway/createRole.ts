import {SecureManagedPolicy, SecureRole, SecureServicePrincipal} from "@amzn/motecdk/mote-iam";
import {Construct} from "constructs";
import {SecureIManagedPolicy} from "@amzn/motecdk/lib/mote-iam/lib/policy";

export class Role {

    private readonly scope: Construct;
    private readonly id: string;
    private readonly managedPolicies: SecureIManagedPolicy[];

    constructor(scope: Construct, id: string, managedPolicies: SecureIManagedPolicy[]) {
        this.scope = scope;
        this.id = id;
        this.managedPolicies = managedPolicies;
    }

    public createLambdaRole(): SecureRole {

        return new SecureRole(this.scope, this.id, {
            assumedBy: new SecureServicePrincipal('lambda.amazonaws.com'),
            managedPolicies: this.managedPolicies,
        });
    }

    public createApiGatewayRole(): SecureRole {

        return new SecureRole(this.scope, this.id, {
            assumedBy: new SecureServicePrincipal('apigateway.amazonaws.com'),
            managedPolicies: this.managedPolicies,
        });
    }
}