package com.icentric.Icentric.simulation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class SimulationDetailResponse {
    private String id;
    private String title;
    @JsonProperty("accent_color")
    private String accentColor;
    private Badge badge;
    @JsonProperty("total_decisions")
    private int totalDecisions;
    private Intro intro;
    private List<Scene> scenes;
    private Results results;

    @Data
    public static class Badge {
        private String icon;
        private String label;
        private String color;
    }

    @Data
    public static class Intro {
        private String icon;
        @JsonProperty("tag_line")
        private String tagLine;
        private String heading;
        private String description;
        private List<MetaItem> meta;
        @JsonProperty("cta_text")
        private String ctaText;
    }

    @Data
    public static class MetaItem {
        private String icon;
        private String label;
    }

    @Data
    public static class Scene {
        private String id;
        private int order;
        @JsonProperty("context_bar")
        private ContextBar contextBar;
        private Object mockup;
        private Question question;
        private Feedback feedback;
        @JsonProperty("next_button_text")
        private String nextButtonText;
    }

    @Data
    public static class ContextBar {
        private String icon;
        private String label;
        private String narrative;
    }

    @Data
    public static class Question {
        @JsonProperty("number_label")
        private String numberLabel;
        private String text;
        @JsonProperty("correct_answer")
        private String correctAnswer;
        private List<Option> options;
    }

    @Data
    public static class Option {
        private String letter;
        private String text;
    }

    @Data
    public static class Feedback {
        private FeedbackEntry correct;
        private Map<String, FeedbackEntry> wrong;
    }

    @Data
    public static class FeedbackEntry {
        private String title;
        private String text;
    }

    @Data
    public static class Results {
        @JsonProperty("score_ring_circumference")
        private double scoreRingCircumference;
        private Map<String, String> titles;
        private Map<String, String> subtitles;
        private List<BreakdownItem> breakdown;
    }

    @Data
    public static class BreakdownItem {
        private String label;
        @JsonProperty("scene_id")
        private String sceneId;
    }
}
