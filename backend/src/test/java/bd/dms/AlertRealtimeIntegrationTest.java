package bd.dms;

import static org.assertj.core.api.Assertions.assertThat;

import bd.dms.alert.AlertService;
import bd.dms.alert.AlertType;
import bd.dms.auth.dto.AuthResponse;
import bd.dms.auth.dto.LoginRequest;
import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
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

/** Mirrors {@code StompTopicAuthIntegrationTest}'s style for the two new alert topics. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AlertRealtimeIntegrationTest {

    private static final String DEMO_PASSWORD = "relief2026";
    private static final long RECEIVE_TIMEOUT_SECONDS = 5;
    private static final long REFUSAL_TIMEOUT_SECONDS = 2;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AlertService alertService;

    @Autowired
    private UserRepository users;

    @Autowired
    private CampRepository camps;

    @Test
    void coordinatorReceivesEveryAlertOnTheCoordinatorTopic() throws Exception {
        StompSession session = connect(tokenFor("coordinator"));
        BlockingQueue<Object> frames = subscribe(session, "/topic/alerts");

        raiseAlert();

        assertThat(frames.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void donorCannotSubscribeToTheCoordinatorAlertTopic() throws Exception {
        StompSession session = connect(tokenFor("donor"));
        BlockingQueue<Object> frames = subscribe(session, "/topic/alerts");

        raiseAlert();

        assertThat(frames.poll(REFUSAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void assignedCampManagerReceivesTheirCampAlertTopic() throws Exception {
        Camp camp = camps.findByCode("jam-kurigram-sadar").orElseThrow();
        StompSession session = connect(tokenFor("camp_manager"));
        BlockingQueue<Object> frames = subscribe(session, "/topic/camp/" + camp.getId() + "/alerts");

        raiseAlertOn(camp);

        assertThat(frames.poll(RECEIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isNotNull();
    }

    private void raiseAlert() {
        raiseAlertOn(camps.findByCode("jam-kurigram-sadar").orElseThrow());
    }

    private void raiseAlertOn(Camp camp) {
        AppUser coordinator = users.findByUsername("coordinator").orElseThrow();
        alertService.raise(coordinator, AlertType.RESOURCE_SHORTAGE, camp.getId(), "Realtime test");
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
