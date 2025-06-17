package com.carcassonne.backend.security

import org.springframework.http.server.ServerHttpRequest
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.support.DefaultHandshakeHandler
import java.security.Principal
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken

class UserHandshakeHandler : DefaultHandshakeHandler() {

    override fun determineUser(
        request: ServerHttpRequest,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Principal? {

        val username = attributes["username"] as? String
            ?: return super.determineUser(request, wsHandler, attributes)

        return UsernamePasswordAuthenticationToken(username, null, emptyList())
    }
}
