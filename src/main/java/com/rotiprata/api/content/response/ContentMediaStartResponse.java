package com.rotiprata.api.content.dto;

import java.util.UUID;

public record ContentMediaStartResponse(UUID contentId, String status, String pollUrl) {}
