package com.amazon.amazonpaymerchanturl.translator;

/**
 * This interface should be implemented to perform translations
 * from input entity to output entity.
 *
 * @param <From> From entity
 * @param <To>   To entity
 */
public interface ITranslator<From, To> {
    To translate(From e);
}
