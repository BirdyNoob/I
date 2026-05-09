package com.icentric.Icentric.common.converter;

import com.icentric.Icentric.common.enums.Department;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Allows Spring to convert a String query/path parameter to a {@link Department}
 * using the same case-insensitive logic as the Jackson {@code @JsonCreator}.
 *
 * Without this, Spring's ConversionService falls back to {@link Enum#valueOf}
 * which is case-sensitive and only accepts the raw enum name (e.g. "ENGINEERING"),
 * causing 400 errors for user-friendly values like "Engineering".
 */
@Component
public class DepartmentConverter implements Converter<String, Department> {

    @Override
    public Department convert(String source) {
        Department dept = Department.fromString(source);
        if (dept == null && !source.isBlank()) {
            throw new IllegalArgumentException(
                    "Unknown department: '" + source + "'. " +
                    "Use the enum name (e.g. ENGINEERING) or display name (e.g. Engineering)."
            );
        }
        return dept;
    }
}
