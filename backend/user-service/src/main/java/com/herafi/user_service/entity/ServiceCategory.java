package com.herafi.user_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Setter
@Getter
@Entity
@Table(name = "service_categories")
public class ServiceCategory {

    @Id
    @Column(length = 50, nullable = false)
    private String code;

    @Column(length = 100, nullable = false, name = "display_name")
    private String displayName;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active = true;
}
