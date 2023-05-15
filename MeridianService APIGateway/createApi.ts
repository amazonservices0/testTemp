import {Construct} from "constructs";
import {ApiIntegrationType, integrationResponse, StatusCodes} from "../constants/apiGateway";
import {
    AccessLogFormat,
    AuthorizationType, AwsIntegration,
    JsonSchema,
    MethodLoggingLevel,
    Model,
    RequestValidator, RestApi,
} from "aws-cdk-lib/aws-apigateway";
import {LogGroup, RetentionDays} from "aws-cdk-lib/aws-logs";
import {LogGroupLogDestination} from "aws-cdk-lib/aws-apigateway";
import {StateMachine} from "aws-cdk-lib/aws-stepfunctions";
import {getRoleName} from "../constants/role";
import {Stage} from "../configurations/stages/stages";
import { StepFunctionIntegration } from "./apigateway/integration/CreateStepFunctionIntegration";
import {Role} from "./createRole";
import { getApiGatewayAsyncStepFunctionIntegrationPolicies } from "../helper/apigateway/ApiGatewayIntegrationHelper";
import {HttpMethod} from "aws-cdk-lib/aws-events";
import {IResource} from "aws-cdk-lib/aws-apigateway/lib/resource";

export interface ApiProps {
    readonly apiName: string;
    readonly stage: Stage,
}

export class Api extends Construct {

    private apiProps: ApiProps;
    private restApi: RestApi;
    private rootFunction: IResource;
    private awsIntegration: AwsIntegration;

    constructor(scope: Construct,
                apiProps: ApiProps) {
        super(scope, apiProps.apiName)
        this.apiProps = apiProps;
        this.restApi = this.createRestApi();
    }

    private createRestApi() {
        const accessLogGroup = new LogGroup(this, this.apiProps.apiName+'-AccessLogs', {
            logGroupName: '/aws/' + this.apiProps.apiName + '-api-gateway-access-logs' + '/',
            retention: RetentionDays.SIX_MONTHS
        });

        return new RestApi(this, this.apiProps.apiName, {
            restApiName: this.apiProps.apiName,
            deploy: true,
            cloudWatchRole: true,
            deployOptions: {
                tracingEnabled: true,
                dataTraceEnabled: true,
                accessLogDestination: new LogGroupLogDestination(accessLogGroup),
                accessLogFormat: AccessLogFormat.jsonWithStandardFields(),
                loggingLevel: MethodLoggingLevel.INFO,
                metricsEnabled: true,
                stageName: this.apiProps.stage.stageName
            }
        });
    }

    public addResource(resourceName: string) {
        this.rootFunction = this.restApi.root.addResource(resourceName);
        return this;
    }

    public addAsyncStepFunctionIntegration(stateMachine: StateMachine){

        this.awsIntegration = new StepFunctionIntegration(this, this.apiProps.apiName,
            stateMachine.stateMachineArn).getAyncStepFunctionIntegration();
        return this;
    }

    public addMethod(httpMethod: string, requestSchema: JsonSchema, responseSchema: JsonSchema) {
        this.rootFunction.addMethod(httpMethod, this.awsIntegration, {
            requestModels: {
                'application/json': this.createModel(this.restApi, 'RequestModel' + httpMethod,
                    requestSchema)
            },
            requestValidator: this.getRequestValidator(this.restApi, this.apiProps.apiName + 'Validator' + httpMethod),
            authorizationType: AuthorizationType.IAM,
            methodResponses: this.getMethodResponses(this.restApi, httpMethod, responseSchema)
        });
        return this;
    }

    private getRequestValidator(restApi: RestApi, id: string) : RequestValidator {
        const requestValidator = new RequestValidator(this, id, {
            requestValidatorName: id,
            restApi: restApi,
            validateRequestBody: true,
            validateRequestParameters: true
        })
        return requestValidator;
    }

    private getMethodResponses(restApi: RestApi, httpMethodSuffix: string, responseSchema: JsonSchema) {
        const responseModel = this.createModel(restApi, 'ResponseModel' + httpMethodSuffix,
            responseSchema as JsonSchema);
        let methodResponses = []
        for (let code of StatusCodes) {
            methodResponses.push({
                    statusCode: code,
                    //If required, add Cross-Origin Resource Policy (CORP) or other header options.
                    responseParameters: {'method.response.header.Content-Type': true},
                    responseModels: {'application/json': responseModel}
                }
            )
        }
        return methodResponses;
    }

    private createModel(restApi: RestApi, idSuffix: string, jsonSchema: JsonSchema) {
       return new Model(this, this.apiProps.apiName + idSuffix, {
            restApi: restApi,
            contentType: 'application/json',
            schema: jsonSchema,
        })
    }
}