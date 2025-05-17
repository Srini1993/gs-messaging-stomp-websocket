package com.example.messagingstompwebsocket;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Type;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.boot.test.web.server.LocalServerPort;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
		properties = {
				"server.port=8443"
		})
public class GreetingIntegrationTests {

	@LocalServerPort
	private int port;

	private WebSocketStompClient stompClient;

	private final WebSocketHttpHeaders headers = new WebSocketHttpHeaders();

	@BeforeEach
	public void setup() throws Exception {
		// Disable certificate validation for test purposes
		TrustManager[] trustAllCerts = new TrustManager[] {
				new X509TrustManager() {
					public void checkClientTrusted(X509Certificate[] certs, String authType) {}
					public void checkServerTrusted(X509Certificate[] certs, String authType) {}
					public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
				}
		};

		SSLContext sc = SSLContext.getInstance("TLS");
		sc.init(null, trustAllCerts, new SecureRandom());
		HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

		WebSocketClient webSocketClient = new StandardWebSocketClient();
		this.stompClient = new WebSocketStompClient(webSocketClient);
		this.stompClient.setMessageConverter(new MappingJackson2MessageConverter());
	}


	//@Test
	public void getGreeting() throws Exception {

		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicReference<Throwable> failure = new AtomicReference<>();

		StompSessionHandler handler = new TestSessionHandler(failure) {

			@Override
			public void afterConnected(final StompSession session, StompHeaders connectedHeaders) {
				session.subscribe("/topic/greetings", new StompFrameHandler() {
					@Override
					public Type getPayloadType(StompHeaders headers) {
						return Greeting.class;
					}

					@Override
					public void handleFrame(StompHeaders headers, Object payload) {
						Greeting greeting = (Greeting) payload;
						try {
							assertEquals("Hello, Spring!", greeting.getContent());
						} catch (Throwable t) {
							System.out.println(t.toString());
							failure.set(t);
						} finally {
							session.disconnect();
							latch.countDown();
						}
					}
				});
				try {
					session.send("/app/hello", new HelloMessage("Spring"));
				} catch (Throwable t) {
					System.out.println(t.toString());
					failure.set(t);
					latch.countDown();
				}
			}
		};

		System.out.println(port);

		this.stompClient.connectAsync("wss://localhost:{port}/gs-guide-websocket", this.headers, handler, this.port);

		if (latch.await(10, TimeUnit.SECONDS)) {
			if (failure.get() != null) {
				throw new AssertionError("", failure.get());
			}
		}
		else {
			fail("Greeting not received");
		}

	}

	private static class TestSessionHandler extends StompSessionHandlerAdapter {

		private final AtomicReference<Throwable> failure;

		public TestSessionHandler(AtomicReference<Throwable> failure) {
			this.failure = failure;
		}

		@Override
		public void handleFrame(StompHeaders headers, Object payload) {
			this.failure.set(new Exception(headers.toString()));
		}

		@Override
		public void handleException(StompSession s, StompCommand c, StompHeaders h, byte[] p, Throwable ex) {
			System.out.println(ex.toString());
			this.failure.set(ex);
		}

		@Override
		public void handleTransportError(StompSession session, Throwable ex) {
			System.out.println(ex.toString());
			this.failure.set(ex);
		}
	}
}
