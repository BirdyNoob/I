package com.icentric.Icentric.config;

import com.icentric.Icentric.common.converter.StringToDepartmentConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final StringToDepartmentConverter stringToDepartmentConverter;

    public WebMvcConfig(StringToDepartmentConverter stringToDepartmentConverter) {
        this.stringToDepartmentConverter = stringToDepartmentConverter;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(stringToDepartmentConverter);
    }
}
