package org.mental_management_center.mmc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 1. Точка входу для фронтенду
        // Саме сюди буде підключатися наш JavaScript (SockJS)
        registry.addEndpoint("/ws-chat").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 2. Брокер повідомлень (від сервера -> клієнту)
        // /topic - для загальних/публічних повідомлень (якщо колись знадобиться)
        // /queue - для приватних повідомлень (наша головна ціль)
        registry.enableSimpleBroker("/topic", "/queue");

        // 3. Префікс для вхідних повідомлень (від клієнта -> серверу)
        // Усі повідомлення, що йдуть у контролери, повинні починатися з /app
        registry.setApplicationDestinationPrefixes("/app");

        // 4. Префікс для ідентифікації конкретного користувача (для приватних повідомлень)
        registry.setUserDestinationPrefix("/user");
    }
}