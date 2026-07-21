package bd.dms.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * The realtime transport: STOMP over a raw WebSocket at {@code /ws}, served by the in-memory
 * simple broker on {@code /topic}. There is no SockJS fallback by design — the client falls back
 * to React Query polling when the socket drops.
 *
 * <p>Clients never send application messages; they only subscribe. Authentication and, crucially,
 * subscription authorization happen in {@link StompAuthChannelInterceptor} on the inbound channel.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authInterceptor;

    public WebSocketConfig(StompAuthChannelInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // The SPA is served from its own origin (nginx proxies /ws), so origins stay permissive
        // here; the bearer token presented at STOMP CONNECT is the actual gate.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
