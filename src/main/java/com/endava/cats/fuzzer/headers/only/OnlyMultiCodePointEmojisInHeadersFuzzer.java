package com.endava.cats.fuzzer.headers.only;

import com.endava.cats.annotations.EmojiFuzzer;
import com.endava.cats.annotations.HeaderFuzzer;
import com.endava.cats.fuzzer.executor.HeadersIteratorExecutor;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzer;
import com.endava.cats.fuzzer.headers.base.BaseHeadersFuzzerContext;
import com.endava.cats.generator.simple.UnicodeGenerator;
import com.endava.cats.http.ResponseCodeFamily;
import com.endava.cats.strategy.FuzzingStrategy;
import jakarta.inject.Singleton;

@Singleton
@HeaderFuzzer
@EmojiFuzzer
public class OnlyMultiCodePointEmojisInHeadersFuzzer extends BaseHeadersFuzzer {

    public OnlyMultiCodePointEmojisInHeadersFuzzer(HeadersIteratorExecutor headersIteratorExecutor) {
        super(headersIteratorExecutor);
    }

    @Override
    public BaseHeadersFuzzerContext createFuzzerContext() {
        return BaseHeadersFuzzerContext.builder()
                .expectedHttpCodeForRequiredHeadersFuzzed(ResponseCodeFamily.FOURXX)
                .expectedHttpForOptionalHeadersFuzzed(ResponseCodeFamily.FOURXX)
                .typeOfDataSentToTheService("values replaced by multi code point emojis")
                .fuzzStrategy(UnicodeGenerator.getMultiCodePointEmojis()
                        .stream().map(value -> FuzzingStrategy.replace().withData(value)).toList())
                .matchResponseSchema(false)
                .build();
    }
}