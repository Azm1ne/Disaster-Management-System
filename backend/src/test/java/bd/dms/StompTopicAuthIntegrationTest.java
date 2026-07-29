package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.dms.auth.dto.AuthResponse;
import bd.dms.auth.dto.LoginRequest;
import bd.dms.sim.SimulationEngine;
import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * The realtime access-control seam: topic subscription <em>is</em> the boundary, so this asserts
 * on what the transport actually delivers. An entitled principal receives pushed world change; an
 * unentitled one receives nothing at all — including a Camp Manager reaching for a camp that is
 * not theirs, which is the case that per-camp binding exists to stop.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StompTopicAuthIntegrationTest {

    private static final String DEMO_PASSWORD = "relief2026";
    /** Long enough for a legitimate frame to arrive; a refused subscription never produces one. */
    private static final long RECEIVE_TIMEOUT_SECONDS = 10;
    /** Long enough for the server's ERROR-and-close answer to a subscription it refuses. */
    private static final long REFUSAL_TIMEOUT_SECONDS = 5;
    /**
     * The JSR-356 client container defaults to an 8 KB text buffer and refuses partial messages, so
     * anything larger closes the socket with 1009 ("message too big") before the frame is handed to
     * the session — and {@code /topic/world} carries the whole world, which grows with the number of
     * disasters on record. A browser negotiates no such limit, so this is purely a harness artifact;
     * without it the test silently starts measuring payload size instead of access control.
     */
    private static final int CLIENT_TEXT_BUFFER_BYTES = 1024 * 1024;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private SimulationEngine engine;

    @Autowired
    private CampRepository camps;

    @BeforeEach
    void resetEngine() {
        // SimulationEngine is a singleton bean shared with every other @SpringBootTest that
        // reuses this context; without resetting, a prior test can leave tick at Scenario.LENGTH,
        // making advance() a no-op that never publishes a WorldChangedEvent for this test to see.
        engine.reset();
    }

    @Test
    void coordinatorReceivesWorldUpdatesAsTheSimulationTicks() throws Exception {
        StompSession session = connect(tokenFor("coordinator"));
        BlockingQueue<Object> frames = subscribe(session, "/topic/world");

        assertThat(awaitFrame(frames, engine::advance))
                .as("an entitled subscriber receives the world")
                .isNotNull();
    }

    @Test
    void anySignedInRoleReceivesTheSimulationClock() throws Exception {
        StompSession session = connect(tokenFor("victim"));
        BlockingQueue<Object> frames = subscribe(session, "/topic/simulation");

        assertThat(awaitFrame(frames, engine::pause))
                .as("the DEMO clock is shared with every signed-in role")
                .isNotNull();
    }

    @Test
    void assignedCampManagerReceivesTheirOwnCampTopic() throws Exception {
        StompSession session = connect(tokenFor("camp_manager"));
        BlockingQueue<Object> frames = subscribe(session, campTopic("jam-kurigram-sadar"));

        assertThat(awaitFrame(frames, engine::advance))
                .as("a manager receives the camp they are assigned to")
                .isNotNull();
    }

    @Test
    void campManagerIsRefusedACampTheyDoNotManage() throws Exception {
        StompSession session = connect(tokenFor("camp_manager"));
        BlockingQueue<Object> frames = subscribe(session, campTopic("jam-chilmari"));

        awaitRefusal(session);
        engine.advance();

        assertThat(frames)
                .as("a manager gets nothing for a camp that is not theirs")
                .isEmpty();
    }

    @Test
    void donorIsRefusedCampTopicsEntirely() throws Exception {
        StompSession session = connect(tokenFor("donor"));
        BlockingQueue<Object> frames = subscribe(session, campTopic("jam-kurigram-sadar"));

        awaitRefusal(session);
        engine.advance();

        assertThat(frames)
                .as("camp detail never reaches a role outside the operation")
                .isEmpty();
    }

    @Test
    void aRealtimeSessionCannotBeOpenedWithoutAToken() {
        assertThatThrownBy(() -> connect(null))
                .as("the socket itself demands credentials")
                .isNotNull();
    }

    private String campTopic(String campCode) {
        Camp camp = camps.findByCode(campCode).orElseThrow();
        return "/topic/camp/" + camp.getId();
    }

    private StompSession connect(String token) throws Exception {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxTextMessageBufferSize(CLIENT_TEXT_BUFFER_BYTES);
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient(container));
        client.setMessageConverter(new MappingJackson2MessageConverter());
        client.setInboundMessageSizeLimit(CLIENT_TEXT_BUFFER_BYTES);
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        return client.connectAsync(
                        "ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private BlockingQueue<Object> subscribe(StompSession session, String destination) {
        BlockingQueue<Object> frames = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Object.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                frames.add(payload);
            }
        });
        return frames;
    }

    /**
     * SUBSCRIBE is fire-and-forget and the simple broker answers no RECEIPT, so a client has no way
     * to observe the moment its subscription registers. Re-trigger rather than sleep: a broadcast
     * that lands before the SUBSCRIBE reaches the broker is simply followed by another one.
     */
    private Object awaitFrame(BlockingQueue<Object> frames, Runnable trigger) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(RECEIVE_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            trigger.run();
            Object frame = frames.poll(500, TimeUnit.MILLISECONDS);
            if (frame != null) {
                return frame;
            }
        }
        return null;
    }

    /**
     * A refused SUBSCRIBE is answered with an ERROR frame and the server drops the session. That
     * close is the only proof the broker actually processed the frame, so wait for it: without it,
     * a subscription that simply had not registered yet is indistinguishable from a refused one,
     * and the two "receives nothing" tests would pass for the wrong reason.
     */
    private void awaitRefusal(StompSession session) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(REFUSAL_TIMEOUT_SECONDS);
        while (session.isConnected() && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertThat(session.isConnected())
                .as("the transport itself refuses the subscription and closes the session")
                .isFalse();
    }

    private String tokenFor(String username) {
        return rest.postForEntity("/auth/login", new LoginRequest(username, DEMO_PASSWORD),
                AuthResponse.class).getBody().accessToken();
    }
}
