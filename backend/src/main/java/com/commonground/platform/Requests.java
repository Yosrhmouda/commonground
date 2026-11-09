package com.commonground.platform;

import jakarta.validation.constraints.*;

public final class Requests {
    private Requests() {}
    public record Edit(@NotBlank @Size(max=100) String title, @NotBlank @Size(max=180) String location,
                       @NotBlank @Size(max=100) String city, @NotBlank @Size(min=2,max=2) String country,
                       @NotBlank @Size(max=1500) String description) {}
    public record Cleanup(@NotBlank @Size(max=100) String organizerName, @NotBlank String date,
                          @NotBlank String time, @NotBlank @Size(max=80) String timeZone,
                          @Min(1) @Max(500) int target, @NotBlank @Size(max=1500) String supplies) {}
    public record Toggle(boolean active) {}
    public record Live(boolean active, @Size(max=2048) String url, boolean consent) {}
}
