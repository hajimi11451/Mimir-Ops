package com.example.backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Step {

    private String id;

    private String description;

    private String hint;

    private String rollbackCmd;

    @Builder.Default
    private boolean risky = false;

    @Builder.Default
    private StepStatus status = StepStatus.PENDING;

    @Builder.Default
    private String result = "";

    @Builder.Default
    private int retryCount = 0;
}
