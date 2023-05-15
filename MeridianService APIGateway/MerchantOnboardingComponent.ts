import {Construct} from "constructs";
import {Role} from "../constructs/createRole";
import {InfraStackProps} from "../stacks/merchantRiskStack";
import {SecureIManagedPolicy} from "@amzn/motecdk/lib/mote-iam/lib/policy";
import {
    BCDSRequestInit,
    BCDSResponsePoller,
    FinalizeOnboardingResponse,
    getMeridianServicePackage,
    InitiateOnboarding
} from "../constants/lambda";
import {Lambda} from "../constructs/createLambda";
import {getResourceName, meridianServicePackage} from "../constants";
import {getRoleName} from "../constants/role";
import {SecureFunction} from "@amzn/motecdk/mote-lambda";
import {createDefinition} from "../helper/stepfunction/OnboardMerchantSfnHelper";
import {StepFunction} from "../constructs/createStepfunction";
import {OnboardMerchantWorkflow} from "../constants/stepfunction";
import {Duration} from "aws-cdk-lib";
import {S3Policies} from "../constructs/policies/S3Policies";
import {bcdsRequestBucketName, bcdsResponseBucketName, getBCDSAccountRoleArn} from "../constants/S3";
import {CreateS3} from "../constructs/createS3";
import {
    getBCDSRequestInitLambdaPolicies,
    getBCDSResponsePollerLambdaPolicies,
    getFinalizeOnboardinglambdaPolicies,
    getInitiateOnboardinglambdaPolicies
} from "../helper/lambda/OnboardMerchantLambdaHelper";
import { Api } from "../constructs/createApi";
import {ApiIntegrationType, OnboardBatchMerchantRiskApi} from "../constants/apiGateway";
import { HttpMethod } from "aws-cdk-lib/aws-events";
import {
    OnboardBatchMerchantRiskApiRequestSchema,
    OnboardBatchMerchantRiskApiResponseSchema
} from "../helper/apigateway/Schemas/OnboardBatchMerchantRiskApiSchemaHelper";

export class MerchantOnboardingComponent extends Construct{

    private readonly merchantOnboardingStepFunction : StepFunction;
    private readonly bcdsRequestInitLambda : SecureFunction;
    private readonly bcdsResponsePollerLambda : SecureFunction;

    constructor(scope: Construct, idSuffix: string, props: InfraStackProps) {

        super(scope, `MerchantOnboardingComponent${idSuffix}`);

        const finalizeOnboardingLambda = this.createLambda(idSuffix, props, FinalizeOnboardingResponse, getFinalizeOnboardinglambdaPolicies(scope, idSuffix));
        const initiateOnboardingLambda = this.createLambda(idSuffix, props, InitiateOnboarding, getInitiateOnboardinglambdaPolicies(scope, idSuffix));
        this.bcdsRequestInitLambda = this.createLambda(idSuffix, props, BCDSRequestInit, getBCDSRequestInitLambdaPolicies(scope, idSuffix));
        this.bcdsResponsePollerLambda = this.createLambda(idSuffix, props, BCDSResponsePoller, getBCDSResponsePollerLambdaPolicies(scope, idSuffix));

        const workflowDefinition = createDefinition(this, idSuffix, {
            initiateOnboardingLambda : initiateOnboardingLambda,
            finalizeOnboardingLambda : finalizeOnboardingLambda,
            bcdsRequestInitLambda : this.bcdsRequestInitLambda,
            bcdsResponsePollerLambda : this.bcdsResponsePollerLambda
        });

        this.merchantOnboardingStepFunction = new StepFunction(scope, {
            definition : workflowDefinition,
            name : getResourceName(OnboardMerchantWorkflow, idSuffix),
            timeout : Duration.hours(5)
        });

        this.createBCDSBuckets(idSuffix, props)


        const onboardBatchMerchantRiskApi = new Api(scope, {
            apiName: getResourceName(OnboardBatchMerchantRiskApi, idSuffix),
            stage: props.stage,
        }).addResource(OnboardBatchMerchantRiskApi)
            .addAsyncStepFunctionIntegration(this.merchantOnboardingStepFunction.getStateMachine())
            .addMethod(HttpMethod.POST, OnboardBatchMerchantRiskApiRequestSchema, OnboardBatchMerchantRiskApiResponseSchema)
    }

    private createBCDSBuckets(idSuffix: string, props: InfraStackProps) {
        const bcdsRequestS3BucketName = getResourceName(bcdsRequestBucketName, idSuffix, props.stage.stageName);
        const bcdsRequestBucket = new CreateS3(this, bcdsRequestS3BucketName).getBucket();
        S3Policies.crossAccountReadActionsS3Policy(bcdsRequestBucket, getBCDSAccountRoleArn(props.stage.stageType));


        const bcdsResponseS3BucketName = getResourceName(bcdsResponseBucketName, idSuffix, props.stage.stageName);
        const bcdsResponseBucket = new CreateS3(this, bcdsResponseS3BucketName).getBucket();
        S3Policies.crossAccountWriteActionsS3Policy(bcdsResponseBucket, getBCDSAccountRoleArn(props.stage.stageType));

    }

    private createLambda(idSuffix: string, props: InfraStackProps,
                         lambda: string, policies: SecureIManagedPolicy[]): SecureFunction {

        const lambdaRole = new Role(this,
            getRoleName(lambda, idSuffix),
            policies);

        return new Lambda(this,
            getResourceName(lambda, idSuffix),
            getMeridianServicePackage(meridianServicePackage),
            lambdaRole.createLambdaRole(),
            {
                stage: props.stage
            }
        ).createLambda();
    }

    public getStepFunction(): StepFunction {
        return this.merchantOnboardingStepFunction;
    }

    public getBCDSRequestInitLambda(): SecureFunction {
        return this.bcdsRequestInitLambda;
    }

    public getBCDSResponsePollerLambda(): SecureFunction {
        return this.bcdsResponsePollerLambda;
    }
}