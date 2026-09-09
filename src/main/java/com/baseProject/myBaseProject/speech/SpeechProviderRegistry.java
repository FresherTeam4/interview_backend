package com.baseProject.myBaseProject.speech;

import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SpeechProviderRegistry {
    private final Map<String, SpeechToTextProvider> speechToTextProviders;
    private final Map<String, TextToSpeechProvider> textToSpeechProviders;

    public SpeechProviderRegistry(
            List<SpeechToTextProvider> speechToTextProviders,
            List<TextToSpeechProvider> textToSpeechProviders) {
        // Lập chỉ mục riêng để STT và TTS có thể dùng hai provider khác nhau.
        this.speechToTextProviders = index(speechToTextProviders, SpeechToTextProvider::name);
        this.textToSpeechProviders = index(textToSpeechProviders, TextToSpeechProvider::name);
    }

    public SpeechToTextProvider speechToText(String name) {
        SpeechToTextProvider provider = speechToTextProviders.get(normalize(name));
        if (provider == null) {
            throw new DomainException(
                    ErrorCode.SPEECH_CONFIG_ERROR,
                    "Unknown speech-to-text provider: " + name);
        }
        return provider;
    }

    public TextToSpeechProvider textToSpeech(String name) {
        TextToSpeechProvider provider = textToSpeechProviders.get(normalize(name));
        if (provider == null) {
            throw new DomainException(
                    ErrorCode.SPEECH_CONFIG_ERROR,
                    "Unknown text-to-speech provider: " + name);
        }
        return provider;
    }

    private static <T> Map<String, T> index(
            List<T> providers, Function<T, String> nameExtractor) {
        return providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> normalize(nameExtractor.apply(provider)),
                Function.identity()));
    }

    private static String normalize(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }
}
