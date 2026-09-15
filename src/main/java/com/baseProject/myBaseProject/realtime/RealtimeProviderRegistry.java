package com.baseProject.myBaseProject.realtime;

import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RealtimeProviderRegistry {
    private final Map<String, RealtimeInterviewProvider> providersByName;

    public RealtimeProviderRegistry(List<RealtimeInterviewProvider> providers) {
        Map<String, RealtimeInterviewProvider> providerMap = new HashMap<>();

        for (RealtimeInterviewProvider provider : providers) {
            String providerName = normalize(provider.name());
            if (providerMap.containsKey(providerName)) {
                throw new IllegalStateException(
                        "Duplicate realtime provider: " + providerName);
            }
            providerMap.put(providerName, provider);
        }

        providersByName = Map.copyOf(providerMap);
    }

    public RealtimeInterviewProvider getProvider(String name) {
        String providerName = normalize(name);
        RealtimeInterviewProvider provider = providersByName.get(providerName);
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
