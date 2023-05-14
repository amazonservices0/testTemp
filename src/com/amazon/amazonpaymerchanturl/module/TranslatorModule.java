package com.amazon.amazonpaymerchanturl.module;

import java.util.List;

import javax.inject.Singleton;

import com.amazon.amazonpaymerchanturl.translator.SQSBatchRequestTranslator;
import com.amazon.amazonpaymerchanturl.translator.ITranslator;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;

import dagger.Module;
import dagger.Provides;

/**
 * Translator configuration class for lambda handler.
 */
@Module
public class TranslatorModule {

    /**
     * Provides sqs batch request translator.
     * @return the translator
     */
    @Singleton
    @Provides
    public ITranslator<List<String>, SendMessageBatchRequest> providesSQSBatchRequestTranslator() {
        return new SQSBatchRequestTranslator();
    }
}
