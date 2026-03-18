package com.icentric.Icentric.identity.dto;

public record CreateUserRequest(

        String email,
        String password,
        String role,
        String department

) {}
