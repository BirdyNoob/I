package com.icentric.Icentric.simulation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class SimulationListResponse {
    private List<SimulationSummary> simulations;

    @Data
    public static class SimulationSummary {
        private String id;
        private String title;
        @JsonProperty("accent_color")
        private String accentColor;
        private Badge badge;
        @JsonProperty("total_decisions")
        private int totalDecisions;
        @JsonProperty("is_attempted")
        private boolean attempted;
        private IntroSummary intro;
    }

    @Data
    public static class Badge {
        private String icon;
        private String label;
        private String color;
    }

    @Data
    public static class IntroSummary {
        private String icon;
        @JsonProperty("tag_line")
        private String tagLine;
        private String heading;
        private List<MetaItem> meta;
    }

    @Data
    public static class MetaItem {
        private String icon;
        private String label;
    }
}
