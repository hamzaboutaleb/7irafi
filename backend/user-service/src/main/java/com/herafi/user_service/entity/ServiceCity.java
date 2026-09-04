package com.herafi.user_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Setter
@Getter
@NoArgsConstructor
@Table(name = "service_cities")
public class ServiceCity {
    @Id
    @Column(length = 50, nullable = false)
    private String code;

    @Column(length = 100, nullable = false)
    private String displayName;

    @Column(nullable = false)
    private boolean active = true;
}
