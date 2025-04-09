package com.carcassonne.backend.controller

import com.carcassonne.backend.security.JwtUtil
import com.carcassonne.backend.security.CustomUserDetailsService
import com.carcassonne.backend.entity.User
import com.carcassonne.backend.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

@RestController
class AuthController(
    private val authManager: AuthenticationManager,
    private val jwtUtil: JwtUtil,
    private val userDetailsService: CustomUserDetailsService,
    private val userService: UserService,
    private val pwEncoder: BCryptPasswordEncoder
) {

}
