package com.rotiprata.api.dto;

import java.util.UUID;

public record ContentMediaStartResponse(UUID contentId, String status, String pollUrl) {}
