package com.impacttracker.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 개발용: /ingest/** 엔드포인트를 인증 없이 호출 가능하게 허용
 * 필요 시 prod 프로파일에선 제거/수정
 */
@Configuration
@Profile({"dev"}) // dev 프로파일에서만 열어두고 싶으면 유지, 항상 열려야 하면 이 라인 삭제
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // CSRF 비활성화(단순 GET 호출 테스트용)
        http.csrf(csrf -> csrf.disable());

        http.authorizeHttpRequests(auth -> auth
                // 인입 수집용 엔드포인트 허용
                .requestMatchers("/ingest/**").permitAll()
                // 그 외는 인증 필요(원하면 .permitAll()로 모두 허용 가능)
                .anyRequest().authenticated()
        );

        // 폼 로그인/기본 인증 막거나 필요 시 켜기
        http.httpBasic(Customizer.withDefaults()); // 필요 없으면 이 줄 삭제

        return http.build();
    }
}
