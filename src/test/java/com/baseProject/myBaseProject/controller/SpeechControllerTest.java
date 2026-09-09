package com.baseProject.myBaseProject.controller;

import com.baseProject.myBaseProject.entity.UserAccount;
import com.baseProject.myBaseProject.enums.UserRole;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.security.CustomUserDetails;
import com.baseProject.myBaseProject.service.SpeechService;
import com.baseProject.myBaseProject.speech.model.SpeechSynthesisResult;
import com.baseProject.myBaseProject.speech.model.SpeechTranscriptionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SpeechControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpeechService service;

    @Test
    void authenticatedUserCanTranscribeAudio() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "answer.webm", "audio/webm", new byte[]{1, 2, 3});
        when(service.transcribe(7L, 51L, audio))
                .thenReturn(new SpeechTranscriptionResult("My answer", "en"));

        mockMvc.perform(multipart("/api/interview-sessions/51/speech/transcriptions")
                        .file(audio)
                        .with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("My answer"))
                .andExpect(jsonPath("$.languageCode").value("en"));

        verify(service).transcribe(7L, 51L, audio);
    }

    @Test
    void authenticatedUserCanGetInterviewerAudio() throws Exception {
        byte[] audio = {4, 5, 6};
        when(service.synthesizeInterviewerTurn(7L, 51L, 91L))
                .thenReturn(new SpeechSynthesisResult(audio, "audio/mpeg"));

        mockMvc.perform(post("/api/interview-sessions/51/speech/turns/91/audio")
                        .with(user(userDetails())))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(header().string("Cache-Control", "max-age=3600, private"))
                .andExpect(content().bytes(audio));
    }

    @Test
    void speechErrorRemainsJsonWhenClientAcceptsAudio() throws Exception {
        when(service.synthesizeInterviewerTurn(7L, 51L, 91L))
                .thenThrow(new DomainException(ErrorCode.SPEECH_CONFIG_ERROR));

        mockMvc.perform(post("/api/interview-sessions/51/speech/turns/91/audio")
                        .accept("audio/mpeg")
                        .with(user(userDetails())))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("SPEECH_CONFIG_ERROR"));
    }

    @Test
    void anonymousUserGetsJsonFromAudioEndpoint() throws Exception {
        mockMvc.perform(post("/api/interview-sessions/51/speech/turns/91/audio")
                        .accept("audio/mpeg"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(service);
    }

    @Test
    void anonymousUserCannotUseSpeechApi() throws Exception {
        MockMultipartFile audio = new MockMultipartFile(
                "audio", "answer.webm", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new byte[]{1});

        mockMvc.perform(multipart("/api/interview-sessions/51/speech/transcriptions")
                        .file(audio))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    private CustomUserDetails userDetails() {
        return new CustomUserDetails(UserAccount.builder()
                .id(7L)
                .email("candidate@example.com")
                .passwordHash("password")
                .role(UserRole.USER)
                .enabled(true)
                .build());
    }
}
