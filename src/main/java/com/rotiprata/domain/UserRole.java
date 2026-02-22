package com.rotiprata.domain;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserRole {

    private UUID id;

    private UUID userId;

    private AppRole role = AppRole.USER;

    private OffsetDateTime assignedAt = OffsetDateTime.now();

    private UUID assignedBy;
}
