package com.ae.ae_SpringServer.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Entity
@Getter
@Setter
public class Ingredient {
    @Id @GeneratedValue
    @Column(name="id")
    private Long id;

    private String name;
}
