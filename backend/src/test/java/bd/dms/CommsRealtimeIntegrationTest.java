package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;

import bd.dms.auth.dto.AuthResponse;
import bd.dms.auth.dto.LoginRequest;
import bd.dms.broadcast.BroadcastService;
import bd.dms.dm.DmService;
import bd.dms.user.AppUser;
import bd.dms.user.Role;
import bd.dms.user.UserRepository;
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

/** Mirrors {@code AlertRealtimeIntegrationTest}'s style for the ticket 12 broadcast/DM topics. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CommsRealtimeIntegrationTest {

    private static final String DEMO_PASSWORD = "relief2026";
    private static final long RECEIVE_TIMEOUT_SECONDS = 5;
    private static final long REFUSAL_TIMEOUT_SECONDS = 2;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private BroadcastService broadcastService;

    @Autowired
    private DmService dmService;

    @Autowired
    private UserRepository users;

    @Test
    void campManagerReceivesABroadcastTargetedAtCampManagers() throws Exception {
        StompSession session = connect(tokenFor("camp_manager"));
        BlockingQueue<Object> frames = subscribe(session, "/topic/broadcasts/CAMP_MANAGER");

        broadcastService.send(coordinator(), Role.CAMP_MANAGER, "Realtime test", "রিয়েলটাইম টেস্ট");

        assertThat(frames.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void volunteerCannotSubscribeToTheCampManagerBroadcastTopic() throws Exception {
        StompSession session = connect(tokenFor("volunteer"));
        BlockingQueue<Object> frames = subscribe(session, "/topic/broadcasts/CAMP_MANAGER");

        broadcastService.send(coordinator(), Role.CAMP_MANAGER, "Realtime test", "রিয়েলটাইম টেস্ট");

        assertThat(frames.poll(REFUSAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void aUserReceivesADmSentToTheirOwnInbox() throws Exception {
        AppUser campManager = users.findByUsername("camp_manager").orElseThrow();
        StompSession session = connect(tokenFor("camp_manager"));
        BlockingQueue<Object> frames = subscribe(session, "/topic/dm/" + campManager.getId());

        dmService.send(coordinator(), campManager.getId(), "Realtime DM test");

        assertThat(frames.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void aUserCannotSubscribeToAnotherUsersDmInbox() throws Exception {
        AppUser campManager = users.findByUsername("camp_manager").orElseThrow();
        StompSession session = connect(tokenFor("donor"));
        BlockingQueue<Object> frames = subscribe(session, "/topic/dm/" + campManager.getId());

        assertThat(frames.poll(REFUSAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNull();
    }

    private AppUser coordinator() {
        return users.findByUsername("coordinator").orElseThrow();
    }

    private StompSession connect(String token) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        return client.connectAsync(
                        "ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private BlockingQueue<Object> subscribe(StompSession session, String destination) throws InterruptedException {
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
        Thread.sleep(500);
        return frames;
    }

    private String tokenFor(String username) {
        return rest.postForEntity("/auth/login", new LoginRequest(username, DEMO_PASSWORD),
                AuthResponse.class).getBody().accessToken();
    }
}
