package com.icentric.Icentric.common.converter;

import com.icentric.Icentric.common.enums.Department;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class StringToDepartmentConverter implements Converter<String, Department> {
    @Override
    public Department convert(String source) {
        return Department.fromString(source);
    }
}
