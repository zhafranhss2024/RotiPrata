package com.rotiprata.api.zdto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectContentRequest(@NotBlank @Size(max = 500) String feedback) {}
