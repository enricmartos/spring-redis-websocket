package com.github.rawsanj.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rawsanj.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static com.github.rawsanj.config.ChatConstants.MESSAGE_TOPIC;

@Component
@Slf4j
public class RedisChatMessagePublisher {

	private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;
	private final ObjectMapper objectMapper;

	public RedisChatMessagePublisher(ReactiveStringRedisTemplate reactiveStringRedisTemplate, ObjectMapper objectMapper) {
		this.reactiveStringRedisTemplate = reactiveStringRedisTemplate;
		this.objectMapper = objectMapper;
	}

	public Mono<Long> publishChatMessage(String message) {
		return Mono.fromCallable(() -> {
			return "localhost";
		}).map(hostName -> {
			ChatMessage chatMessage = new ChatMessage(message);
			String chatString = "EMPTY_MESSAGE";
			try {
				chatString = objectMapper.writeValueAsString(chatMessage);
			} catch (JsonProcessingException e) {
				log.error("Error converting ChatMessage {} into string", chatMessage, e);
			}
			return chatString;
		}).flatMap(chatString -> {
			// Publish Message to Redis Channels
			return reactiveStringRedisTemplate.convertAndSend(MESSAGE_TOPIC, chatString)
				.doOnSuccess(aLong -> log.info("Message published to Redis Topic."))
				.doOnError(throwable -> log.error("Error publishing message.", throwable));
		});
	}

}
