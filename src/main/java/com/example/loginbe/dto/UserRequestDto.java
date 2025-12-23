package com.example.loginbe.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserRequestDto {
    private String username;
    private String email;
    private String password;
    private String phone;
}