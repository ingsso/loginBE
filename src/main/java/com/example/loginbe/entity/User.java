package com.example.loginbe.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    private String email;

    @Column(unique = true)
    private String socialId;

    private String provider;

    private String password;

    private String role;

    private String phone;

    @Column(length = 500)
    private String refreshToken;

    public User(String socialId, String provider, String email, String role) {
        this.socialId = socialId;
        this.provider = provider;
        this.email = email;
        this.role = role;
    }
}