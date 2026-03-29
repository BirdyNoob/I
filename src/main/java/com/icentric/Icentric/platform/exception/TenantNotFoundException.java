package com.icentric.Icentric.platform.exception;

import com.icentric.Icentric.common.exception.DomainException;

import java.util.UUID;

public class TenantNotFoundException extends DomainException {
    public TenantNotFoundException(String message) {
        super(message);
    }
    
    public TenantNotFoundException(UUID tenantId) {
        super("Tenant with ID " + tenantId + " was not found");
    }
}
