package com.endava.cats.model;

import com.endava.cats.model.strategy.*;
import org.apache.commons.lang3.StringUtils;

/**
 * Encapsulates various fuzzing strategies:
 * <ul>
 * <li>REPLACE - when the fuzzed value replaces the one generated by the PayloadGenerator</li>
 * <li>TRAIL - trails the current value with the given string</li>
 * <li>PREFIX - prefixes the current value with the given string</li>
 * <li>SKIP - doesn't do anything to the current value</li>
 * <li>NOOP - returns the given string</li>
 * </ul>
 */
public abstract class FuzzingStrategy {

    protected String data;

    public static FuzzingStrategy prefix() {
        return new PrefixFuzzingStrategy();
    }

    public static FuzzingStrategy noop() {
        return new NoopFuzzingStrategy();
    }

    public static FuzzingStrategy replace() {
        return new ReplaceFuzzingStrategy();
    }

    public static FuzzingStrategy skip() {
        return new SkipFuzzingStrategy();
    }

    public static FuzzingStrategy trail() {
        return new TrailFuzzingStrategy();
    }

    public static String mergeFuzzing(String fuzzedValue, String suppliedValue, String innerValue) {
        FuzzingStrategy currentStrategy = fromValue(fuzzedValue, innerValue);

        return currentStrategy.process(suppliedValue);
    }

    public static FuzzingStrategy fromValue(String value, String innerValue) {
        if (StringUtils.isBlank(value)) {
            return new ReplaceFuzzingStrategy().withData(innerValue);
        }
        if (value.startsWith(" ") || value.startsWith("\t")) {
            return new PrefixFuzzingStrategy().withData(innerValue);
        }
        if (value.endsWith(" ") || value.endsWith("\t")) {
            return new TrailFuzzingStrategy().withData(innerValue);
        }

        return new ReplaceFuzzingStrategy().withData(value);
    }

    public FuzzingStrategy withData(String inner) {
        this.data = inner;
        return this;
    }

    public String getData() {
        return this.data;
    }

    public boolean isSkip() {
        return this.getClass().isAssignableFrom(SkipFuzzingStrategy.class);
    }

    @Override
    public String toString() {
        if (data != null) {
            return this.name() + " with " + data;
        }
        return this.name();
    }

    public String truncatedValue() {
        if (data != null) {
            String toPrint = data;
            if (data.length() > 30) {
                toPrint = data.substring(0, 30) + "...";
            }
            return this.name() + " with " + toPrint;
        }
        return this.name();
    }

    public String process(Object value) {
        return this.process(String.valueOf(value));
    }

    public abstract String process(String value);

    public abstract String name();
}
