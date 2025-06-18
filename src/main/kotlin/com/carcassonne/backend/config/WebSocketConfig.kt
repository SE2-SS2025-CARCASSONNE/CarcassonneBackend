package com.carcassonne.backend.config
import com.carcassonne.backend.security.JwtHandshakeInterceptor
import com.carcassonne.backend.security.UserHandshakeHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val customHandshakeInterceptor: JwtHandshakeInterceptor
) : WebSocketMessageBrokerConfigurer {

    @Bean
    fun userHandshakeHandler() = UserHandshakeHandler()

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws/game")
            .setAllowedOriginPatterns("*")
            .addInterceptors(customHandshakeInterceptor)
            .setHandshakeHandler(userHandshakeHandler())
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic", "/queue") //Add "/queue" for private messaging
        registry.setApplicationDestinationPrefixes("/app")
        registry.setUserDestinationPrefix("/user")
    }
}
