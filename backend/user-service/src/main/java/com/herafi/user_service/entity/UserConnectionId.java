package com.herafi.user_service.entity;

import java.io.Serializable;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@EqualsAndHashCode
@NoArgsConstructor
@Getter
@Setter
public class UserConnectionId implements Serializable {
    private UUID ownerId;
    private UUID connectionId;

}
