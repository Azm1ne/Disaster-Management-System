package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bd.dms.auth.dto.AuthResponse;
import bd.dms.auth.dto.LoginRequest;
import bd.dms.sim.SimulationEngine;
import bd.dms.world.Camp;
import bd.dms.world.CampRepository;
import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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
    private static final long RECEIVE_TIMEOUT_SECONDS = 5;
    private static final long REFUSAL_TIMEOUT_SECONDS = 2;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private SimulationEngine engine;

    @Autowired
    private CampRepository camps;

    @Test
    void coordinatorReceivesWorldUpdatesAsTheSimulationTicks() throws Exception {
        StompSession session = connect(tokenFor("coordinator"));
        BlockingQueue<Object> frames = subscribe(session, "/topic/world");

        engine.advance();

        assertThat(frames.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("an entitled subscriber receives the world")
                .isNotNull();
    }

    @Test
    void anySignedInRoleReceivesTheSimulationClock() throws Exception {
        StompSession session = connect(tokenFor("victim"));
        BlockingQueue<Object> frames = subscribe(session, "/topic/simulation");

        engine.pause();

        assertThat(frames.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("the DEMO clock is shared with every signed-in role")
                .isNotNull();
    }

    @Test
    void assignedCampManagerReceivesTheirOwnCampTopic() throws Exception {
        StompSession session = connect(tokenFor("camp_manager"));
        BlockingQueue<Object> frames = subscribe(session, campTopic("jam-kurigram-sadar"));

        engine.advance();

        assertThat(frames.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("a manager receives the camp they are assigned to")
                .isNotNull();
    }

    @Test
    void campManagerIsRefusedACampTheyDoNotManage() throws Exception {
        StompSession session = connect(tokenFor("camp_manager"));
        BlockingQueue<Object> frames = subscribe(session, campTopic("jam-chilmari"));

        engine.advance();

        assertThat(frames.poll(REFUSAL_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("a manager gets nothing for a camp that is not theirs")
                .isNull();
    }

    @Test
    void donorIsRefusedCampTopicsEntirely() throws Exception {
        StompSession session = connect(tokenFor("donor"));
        BlockingQueue<Object> frames = subscribe(session, campTopic("jam-kurigram-sadar"));

        engine.advance();

        assertThat(frames.poll(REFUSAL_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("camp detail never reaches a role outside the operation")
                .isNull();
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
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
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

    private BlockingQueue<Object> subscribe(StompSession session, String destination)
            throws InterruptedException {
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
        // SUBSCRIBE is fire-and-forget; let it reach the broker before the world is changed.
        Thread.sleep(500);
        return frames;
    }

    private String tokenFor(String username) {
        return rest.postForEntity("/auth/login", new LoginRequest(username, DEMO_PASSWORD),
                AuthResponse.class).getBody().accessToken();
    }
}
