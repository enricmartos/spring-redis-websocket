package com.github.rawsanj.handler;

import com.github.rawsanj.messaging.RedisChatMessagePublisher;
import com.github.rawsanj.model.ChatMessage;
import com.github.rawsanj.util.ObjectStringConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

@Slf4j
public class ChatWebSocketHandler implements WebSocketHandler {

	private final Sinks.Many<ChatMessage> chatMessageSink;
	private final Flux<ChatMessage> chatMessageFluxSink;
	private final RedisChatMessagePublisher redisChatMessagePublisher;
	private final ObjectStringConverter objectStringConverter;

	public ChatWebSocketHandler(Sinks.Many<ChatMessage> chatMessageSink, RedisChatMessagePublisher redisChatMessagePublisher,
								ObjectStringConverter objectStringConverter) {
		this.chatMessageSink = chatMessageSink;
		this.chatMessageFluxSink = chatMessageSink.asFlux();
		this.redisChatMessagePublisher = redisChatMessagePublisher;
		this.objectStringConverter = objectStringConverter;
	}

	@Override
	public Mono<Void> handle(WebSocketSession webSocketSession) {
		Flux<WebSocketMessage> sendMessageFlux = chatMessageFluxSink.flatMap(objectStringConverter::objectToString)
			.map(webSocketSession::textMessage)
			.doOnError(throwable -> log.error("Error Occurred while sending message to WebSocket.", throwable));
		Mono<Void> outputMessage = webSocketSession.send(sendMessageFlux);

		Mono<Void> inputMessage = webSocketSession.receive()
			.flatMap(webSocketMessage -> redisChatMessagePublisher.publishChatMessage(webSocketMessage.getPayloadAsText()))
			.doOnSubscribe(subscription -> {
				log.info("User '{}' Connected.", webSocketSession.getId());
				chatMessageSink.tryEmitNext(new ChatMessage("CONNECTED"));
			})
			.doOnError(throwable -> log.error("Error Occurred while sending message to Redis.", throwable))
			.doFinally(signalType -> {
				log.info("User '{}' Disconnected.", webSocketSession.getId());
				chatMessageSink.tryEmitNext(new ChatMessage("DISCONNECTED"));
			})
			.then();

		return Mono.zip(inputMessage, outputMessage).then();
	}

	public Mono<Sinks.EmitResult> sendMessage(ChatMessage chatMessage) {
		return Mono.fromSupplier(() -> chatMessageSink.tryEmitNext(chatMessage))
			.doOnSuccess(emitResult -> {
				if (emitResult.isFailure()) {
					log.error("Failed to send message: {}", chatMessage.getMessage());
				}
			});
	}

}
