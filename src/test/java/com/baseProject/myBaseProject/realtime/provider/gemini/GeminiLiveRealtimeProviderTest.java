package com.baseProject.myBaseProject.realtime.provider.gemini;

import com.baseProject.myBaseProject.config.properites.GeminiLiveProperties;
import com.baseProject.myBaseProject.enums.RealtimeTransport;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.realtime.model.RealtimeSessionGrant;
import com.baseProject.myBaseProject.realtime.model.RealtimeSessionSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@ExtendWith(OutputCaptureExtension.class)
class GeminiLiveRealtimeProviderTest {
    private static final Instant NOW = Instant.parse("2026-09-11T08:00:00Z");

    private MockRestServiceServer server;
    private GeminiLiveRealtimeProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new GeminiLiveRealtimeProvider(
                builder.build(), properties(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsSingleUseTokenWithRestrictedSetup() {
        server.expect(once(), requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/auth_tokens"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "uses": 1,
                          "expireTime": "2026-09-11T08:45:00Z",
                          "newSessionExpireTime": "2026-09-11T08:01:00Z",
                          "bidiGenerateContentSetup": {
                            "model": "models/gemini-live-test",
                            "generationConfig": {
                              "responseModalities": ["AUDIO"],
                              "speechConfig": {
                                "voiceConfig": {
                                  "prebuiltVoiceConfig": {"voiceName": "Kore"}
                                }
                              }
                            },
                            "systemInstruction": {
                              "parts": [{"text": "Interview safely"}]
                            }
                          }
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "name": "authTokens/test-token",
                          "expireTime": "2026-09-11T08:44:30Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        RealtimeSessionGrant grant = provider.createSession(
                new RealtimeSessionSpec(30, "kore", "Interview safely"));

        assertThat(grant.provider()).isEqualTo("gemini-live");
        assertThat(grant.transport()).isEqualTo(RealtimeTransport.WEBSOCKET);
        assertThat(grant.ephemeralToken()).isEqualTo("authTokens/test-token");
        assertThat(grant.voiceName()).isEqualTo("Kore");
        assertThat(grant.expiresAt()).isEqualTo("2026-09-11T08:44:30Z");
        assertThat(grant.sessionSetup())
                .containsEntry("model", "models/gemini-live-test")
                .doesNotContainKey("systemInstruction");
        server.verify();
    }

    @Test
    void resumeIncludesStrippedHandleInClientSetup() {
        server.expect(once(), requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/auth_tokens"))
                .andExpect(content().json("""
                        {
                          "bidiGenerateContentSetup": {
                            "sessionResumption": {"handle": "resume-123"}
                          }
                        }
                        """))
                .andRespond(withSuccess("""
                        {"name":"authTokens/resumed","expireTime":"2026-09-11T08:45:00Z"}
                        """, MediaType.APPLICATION_JSON));

        RealtimeSessionGrant grant = provider.resumeSession(
                new RealtimeSessionSpec(30, "Kore", "Interview safely"),
                " resume-123 ");

        assertThat(grant.sessionSetup().get("sessionResumption"))
                .isEqualTo(Map.of("handle", "resume-123"));
        server.verify();
    }

    @Test
    void rateLimitReturnsProviderUnavailable() {
        server.expect(once(), requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/auth_tokens"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> provider.createSession(
                new RealtimeSessionSpec(30, "Kore", "Interview safely")))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.REALTIME_PROVIDER_UNAVAILABLE));
        server.verify();
    }

    @Test
    void invalidProviderExpiryUsesRequestedExpiryAndLogsWarning(CapturedOutput output) {
        server.expect(once(), requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/auth_tokens"))
                .andRespond(withSuccess("""
                        {"name":"authTokens/test-token","expireTime":"invalid"}
                        """, MediaType.APPLICATION_JSON));

        RealtimeSessionGrant grant = provider.createSession(
                new RealtimeSessionSpec(30, "Kore", "Interview safely"));

        assertThat(grant.expiresAt()).isEqualTo("2026-09-11T08:45:00Z");
        assertThat(output).contains("Gemini Live returned invalid expireTime=invalid");
        server.verify();
    }

    private GeminiLiveProperties properties() {
        return new GeminiLiveProperties(
                URI.create("https://generativelanguage.googleapis.com"),
                URI.create("wss://generativelanguage.googleapis.com/live"),
                "test-key",
                "gemini-live-test",
                "Kore",
                List.of("Kore", "Puck"),
                Duration.ofSeconds(5),
                Duration.ofSeconds(15),
                Duration.ofMinutes(1),
                Duration.ofMinutes(15),
                16_000,
                24_000);
    }
}
