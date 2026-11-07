package com.cqr.interviewworkshop.auth.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentPrincipal {

    private Long userId;

    private String username;
}
