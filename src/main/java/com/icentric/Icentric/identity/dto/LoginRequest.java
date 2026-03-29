package com.icentric.Icentric.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Login payload — tenant slug is NO LONGER required.
 * The system resolves the user globally by email.
 */
public record LoginRequest(

        @NotBlank @Email String email,
        @NotBlank String password

) {}
