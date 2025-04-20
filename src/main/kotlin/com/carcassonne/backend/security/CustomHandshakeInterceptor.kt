package com.carcassonne.backend.security

import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService

@Component
//Custom interceptor that runs once during initial WebSocket handshake (like JwtFilter but for WebSockets)
class CustomHandshakeInterceptor(
    private val jwtUtil: JwtUtil,
    private val userDetailsService: UserDetailsService
) : HandshakeInterceptor {

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        val authHeader = request.headers.getFirst("Authorization") //Read auth header from HTTP request

        if (authHeader != null && authHeader.startsWith("Bearer ")) { //Check if authHeader exists and Bearer scheme is used
            val token = authHeader.substring(7) //Extract JWT by removing "Bearer " prefix

            try {
                val username = jwtUtil.extractUsername(token)
                val userDetails = userDetailsService.loadUserByUsername(username) //Load user details from database

                if (jwtUtil.tokenValid(token, userDetails)) { //Check if JWT is valid for this user
                    val authToken = UsernamePasswordAuthenticationToken( //Create Spring Security auth token
                        userDetails, null, userDetails.authorities)

                    SecurityContextHolder.getContext().authentication = authToken //Store authToken in security context
                    return true
                }

            } catch (_: Exception) {
                //Handle error if token invalid or expired
                response.setStatusCode(HttpStatus.UNAUTHORIZED)
            }
        }
        //Reject connection if token is invalid or missing
        response.setStatusCode(HttpStatus.UNAUTHORIZED)
        return false
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        ex: Exception?
    ) {
        //Nothing to do after handshake
    }
}