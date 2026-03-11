package com.icentric.Icentric.content.dto;

public record CreateTrackRequest(

        String slug,
        String title,
        String description,
        String department,
        String trackType,
        Integer estimatedMins

) {}
