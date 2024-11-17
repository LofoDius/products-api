package lofod.productsapi.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val sessionRequestFilter: SessionRequestFilter
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { cors ->
                cors.configurationSource(corsConfigurationSource())
            }
            .authorizeHttpRequests { httpRequests ->
                httpRequests.anyRequest().permitAll()
            }
            .csrf { csrf ->
                csrf.disable()
            }
            .sessionManagement { sessionManager ->
                sessionManager.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .addFilterBefore(sessionRequestFilter, UsernamePasswordAuthenticationFilter::class.java)
            .headers { headers ->
                headers.addHeaderWriter { _, response ->
                    response.setHeader("Referrer-Policy", "no-referrer-when-downgrade")
                }
            }

        return http.build()
    }

    @Bean
    fun bCryptPasswordEncoder(): BCryptPasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource = CorsConfiguration()
        .apply {
            allowedOrigins = listOf("*")
            allowedHeaders = listOf("*")
            allowedMethods = listOf("*")
            exposedHeaders = listOf("Referrer-Policy")
        }.let { corsConfiguration ->
            UrlBasedCorsConfigurationSource().apply {
                registerCorsConfiguration(
                    "/**",
                    corsConfiguration
                )
            }
        }


}
