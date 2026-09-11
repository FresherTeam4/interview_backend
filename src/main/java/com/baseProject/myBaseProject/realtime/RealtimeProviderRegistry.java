package com.baseProject.myBaseProject.realtime;

import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RealtimeProviderRegistry {
    private final Map<String, RealtimeInterviewProvider> providers;

    public RealtimeProviderRegistry(List<RealtimeInterviewProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> normalize(provider.name()), Function.identity()));
    }

    public RealtimeInterviewProvider provider(String name) {
        RealtimeInterviewProvider provider = providers.get(normalize(name));
        if (provider == null) {
            throw new DomainException(
                    ErrorCode.REALTIME_CONFIG_ERROR,
                    "Unknown realtime interview provider: " + name);
        }
        return provider;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }
}
