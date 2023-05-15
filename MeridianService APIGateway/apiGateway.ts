import {IntegrationResponse} from "aws-cdk-lib/aws-apigateway";

export const StatusCodes: string[] = ['200', '202', '400', '404', '500']

export const GetBatchMerchantRiskApi = 'GetBatchMerchantRiskApi';
export const OnboardBatchMerchantRiskApi = 'OnboardBatchMerchantRiskApi';

export enum ApiIntegrationType {
    AsyncStepFunctionIntegration = "AsyncStepFunctionIntegration"
}

export const integrationResponse: IntegrationResponse[] = [
    {
        statusCode: '202',
        responseTemplates: {
            "application/json": JSON.stringify({
                message: "Request is queued for execution"
            })
        },
        responseParameters: {
            'method.response.header.Content-Type': "'application/json'",
        }
    },
    {
        statusCode: '400',
        responseTemplates: {
            "application/json": JSON.stringify({
                message: "Bad Request"
            })
        },
        responseParameters: {
            'method.response.header.Content-Type': "'application/json'",
        }
    },
    {
        statusCode: '404',
        responseTemplates: {
            "application/json": JSON.stringify({
                message: "Not Found"
            })
        },
        responseParameters: {
            'method.response.header.Content-Type': "'application/json'",
        }
    },
    {
        statusCode: '500',
        responseTemplates: {
            "application/json": JSON.stringify({
                message: "Internal Server Error"
            })
        },
        responseParameters: {
            'method.response.header.Content-Type': "'application/json'",
        }
    },
]
