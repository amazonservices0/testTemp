import {JsonSchema, JsonSchemaType} from "aws-cdk-lib/aws-apigateway";
import { merchantIdPattern } from "../../../constants";

export const GetBatchMerchantRiskApiRequestSchema =  {
    type: JsonSchemaType.OBJECT,
    properties: {
        requestId: {
            type: JsonSchemaType.STRING,
            minLength: 1,
            maxLength: 64
        },
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
        responseS3Bucket: {
            type: JsonSchemaType.STRING
        },
        requestFreshData: {
            type: JsonSchemaType.BOOLEAN
        }
    },
    required: [
        'requestId',
        'marketplaceIdMerchantIdsMap',
        'responseS3Bucket',
    ]
}

export const GetBatchMerchantRiskApiResponseSchema = {
    type: JsonSchemaType.OBJECT,
    properties: {
        statusCode: {type: JsonSchemaType.INTEGER},
        body: {type: JsonSchemaType.STRING},
    }
};