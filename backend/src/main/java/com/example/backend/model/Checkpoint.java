package com.example.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Checkpoint {

    private String stepId;

    private String stepDescription;

    private long timestamp;

    private String result;

    @Builder.Default
    private Map<String, String> systemSnapshot = new LinkedHashMap<>();
}
