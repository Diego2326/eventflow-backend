package com.eventflow.eventflow_api.auth.infrastructure.security


import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val authenticator: JwtSessionAuthenticator
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val header = request.getHeader("Authorization")
        if (header?.startsWith("Bearer ") == true) {
            val authentication = authenticator.authenticate(header.substring(7))
            if (authentication != null) SecurityContextHolder.getContext().authentication = authentication
            else SecurityContextHolder.clearContext()
        }
        chain.doFilter(request, response)
    }
}
