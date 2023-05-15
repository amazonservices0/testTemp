import {Construct} from "constructs";
import {Role} from "../constructs/createRole";
import {InfraStackProps} from "../stacks/merchantRiskStack";
import {getResourceName, meridianServicePackage} from "../constants";
import {GetBatchMerchantRisk, getMeridianServicePackage} from "../constants/lambda";
import {Lambda} from "../constructs/createLambda";
import {getRoleName} from "../constants/role";
import {StepFunction} from "../constructs/createStepfunction";
import {createGetBatchSfnDefinition} from "../helper/stepfunction/GetBatchMerchantSfnHelper";
import {Topic} from "aws-cdk-lib/aws-sns";
import {GetBatchMerchantWorkflow} from "../constants/stepfunction";
import {getBatchMerchantLambdaPolicies} from "../helper/lambda/GetBatchMerchantLambdaHelper";
import {Api} from "../constructs/createApi";
import {ApiIntegrationType, GetBatchMerchantRiskApi} from "../constants/apiGateway";
import {HttpMethod} from "aws-cdk-lib/aws-events";
import {
    GetBatchMerchantRiskApiRequestSchema,
    GetBatchMerchantRiskApiResponseSchema
} from "../helper/apigateway/Schemas/GetBatchMerchantRiskApiSchemaHelper";

export class GetBatchMerchantComponent extends Construct {

    constructor(scope: Construct, idSuffix: string, props: InfraStackProps, merchantOnboardingSfn: StepFunction) {

        super(scope, `GetBatchMerchantComponent${idSuffix}`);

        const getBatchMerchantRiskRole = new Role(this, getRoleName(GetBatchMerchantRisk, idSuffix),
            getBatchMerchantLambdaPolicies(this, idSuffix));

        const getBatchMerchantRiskLambda = new Lambda(this,
            getResourceName(GetBatchMerchantRisk, idSuffix),
            getMeridianServicePackage(meridianServicePackage),
            getBatchMerchantRiskRole.createLambdaRole(),
            {
                stage: props.stage
            }
        ).createLambda();

        const workflowDefinition = createGetBatchSfnDefinition(this, idSuffix, {
            getBatchMerchantRiskLambda: getBatchMerchantRiskLambda,
            merchantOnboardingSfn: merchantOnboardingSfn,
            snsArnTopic: new Topic(this, `sns-topic-${idSuffix}`),
        });

        const getBatchMerchantStepFunction = new StepFunction(this, {
            definition: workflowDefinition,
            name: getResourceName(GetBatchMerchantWorkflow, idSuffix),
        });

        const getBatchMerchantRiskApi = new Api(this, {
            apiName: getResourceName(GetBatchMerchantRiskApi, idSuffix),
            stage: props.stage,
        }).addResource(GetBatchMerchantRiskApi)
            .addAsyncStepFunctionIntegration(getBatchMerchantStepFunction.getStateMachine())
            .addMethod(HttpMethod.POST, GetBatchMerchantRiskApiRequestSchema, GetBatchMerchantRiskApiResponseSchema);
    }
}