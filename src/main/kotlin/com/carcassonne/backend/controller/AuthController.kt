package com.carcassonne.backend.controller

import com.carcassonne.backend.dto.LoginRequest
import com.carcassonne.backend.security.JwtUtil
import com.carcassonne.backend.security.CustomUserDetailsService
import com.carcassonne.backend.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.web.bind.annotation.RequestMapping

@RestController
@RequestMapping("/api/auth") //Base bath
@Tag(name = "Auth", description = "User login and registration endpoints")
class AuthController(
    private val authManager: AuthenticationManager,
    private val jwtUtil: JwtUtil,
    private val userDetailsService: CustomUserDetailsService,
    private val userService: UserService,
    private val pwEncoder: BCryptPasswordEncoder,
) {

    @PostMapping("/login") //User login endpoint
    fun login(@RequestBody loginRequest: LoginRequest): ResponseEntity<Map<String, String>> {
        return try {
            authManager.authenticate( //Checks credentials provided by user against DB
                UsernamePasswordAuthenticationToken(loginRequest.username, loginRequest.password))

            val userDetails = userDetailsService.loadUserByUsername((loginRequest.username))
            val token = jwtUtil.createToken(userDetails.username) //Creates JWT from user details

            ResponseEntity.ok(mapOf("token" to token)) //Returns HTTP 200 with JWT mapped to "token" key in response body

        } catch (e: BadCredentialsException) { //Handles invalid credentials (incorrect username or password)
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("message" to "Invalid username or password. Please try again."))
        } catch (e: Exception) { //Handles all other unexpected errors
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("message" to "Something went wrong on our end. Please try again later."))
        }
    }
}
