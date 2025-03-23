package com.carcassonne.backend.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/game")
@Tag(name = "Game", description = "Game control endpoints")
class GameController {

    @Operation(summary = "Ping the server", description = "Used to check if backend is alive")
    @GetMapping("/ping")
    fun ping(): String = "pong"
}
