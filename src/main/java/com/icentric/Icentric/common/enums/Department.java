package com.icentric.Icentric.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Department {
    ENGINEERING("Engineering"),
    SALES("Sales"),
    MARKETING("Marketing"),
    LEGAL("Legal"),
    PRODUCT("Product"),
    MANAGEMENT("Management"),
    OPERATIONS("Operations"),
    QUALITY_ASSURANCE("Quality Assurance"),
    CUSTOMER_SUCCESS("Customer Success"),
    BUSINESS_DEVELOPMENT("Business Development"),
    FINANCE("Finance"),
    HUMAN_RESOURCES("Human Resources"),
    IT_INFORMATION_SECURITY("IT / Information Security"),
    COMMUNICATIONS_PR("Communications / PR"),
    FACILITIES("Facilities"),
    PROCUREMENT("Procurement");

    private final String displayName;

    Department(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Tries to find a matching department by either enum name or display name.
     * Useful for parsing bulk uploads or API requests.
     */
    @JsonCreator
    public static Department fromString(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (Department dept : Department.values()) {
            if (dept.name().equalsIgnoreCase(text.trim()) || 
                dept.displayName.equalsIgnoreCase(text.trim())) {
                return dept;
            }
        }
        // Fallback or throw exception based on strictness. Returning null for unknown values.
        return null;
    }
}
