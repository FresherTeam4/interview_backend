package com.baseProject.myBaseProject.speech;

import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpeechProviderRegistryTest {

    @Test
    void resolvesSpeechToTextAndTextToSpeechIndependently() {
        SpeechToTextProvider azureStt = mock(SpeechToTextProvider.class);
        TextToSpeechProvider elevenLabsTts = mock(TextToSpeechProvider.class);
        when(azureStt.name()).thenReturn("azure");
        when(elevenLabsTts.name()).thenReturn("elevenlabs");
        SpeechProviderRegistry registry = new SpeechProviderRegistry(
                List.of(azureStt), List.of(elevenLabsTts));

        assertThat(registry.speechToText("AZURE")).isSameAs(azureStt);
        assertThat(registry.textToSpeech(" elevenlabs ")).isSameAs(elevenLabsTts);
    }

    @Test
    void unknownProviderReturnsConfigurationError() {
        SpeechProviderRegistry registry = new SpeechProviderRegistry(List.of(), List.of());

        assertThatThrownBy(() -> registry.speechToText("missing"))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.SPEECH_CONFIG_ERROR));
    }
}
