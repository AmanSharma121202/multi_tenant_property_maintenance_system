package com.housing.billing.config.converters;

import java.time.Instant;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

@WritingConverter
public class InstantToStringConverter implements Converter<Instant, String> {
    @Override
    public String convert(Instant source) {
        return source == null ? null : source.toString();
    }
}

