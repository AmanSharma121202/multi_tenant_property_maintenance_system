package com.housing.billing.config.converters;

import java.time.Instant;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public class StringToInstantConverter implements Converter<String, Instant> {
    @Override
    public Instant convert(String source) {
        return (source == null || source.isBlank()) ? null : Instant.parse(source);
    }
}

