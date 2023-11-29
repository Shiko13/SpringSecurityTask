package com.epam.model.dto;

import lombok.Data;

@Data
public class AuthResponseDto {
    private String tokenType = "Bearer";
    private String accessToken;
    private Long userId;
}
