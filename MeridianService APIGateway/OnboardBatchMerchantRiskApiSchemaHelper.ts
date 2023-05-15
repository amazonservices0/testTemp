import {JsonSchema, JsonSchemaType} from "aws-cdk-lib/aws-apigateway";
import { merchantIdPattern } from "../../../constants";


export const OnboardBatchMerchantRiskApiRequestSchema =  {
    type: JsonSchemaType.OBJECT,
    properties: {
        marketplaceIdMerchantIdsMap : {
            type: JsonSchemaType.OBJECT,
            minProperties: 1,
            additionalProperties: {
                type: JsonSchemaType.ARRAY,
                minItems: 1,
                items: {
                    type: JsonSchemaType.STRING,
                    pattern: merchantIdPattern,
                    minLength: 1
                }
            }
        },
        requester: {
            type: JsonSchemaType.STRING,
            minLength: 1,
            maxLength: 256
        }
    },
    required: [
        'requester',
        'marketplaceIdMerchantIdsMap'
    ]
}

export const OnboardBatchMerchantRiskApiResponseSchema = {
    type: JsonSchemaType.OBJECT,
    properties: {
        statusCode: {type: JsonSchemaType.INTEGER},
        body: {type: JsonSchemaType.STRING},
    }
}