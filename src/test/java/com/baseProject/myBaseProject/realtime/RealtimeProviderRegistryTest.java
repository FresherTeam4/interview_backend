package com.baseProject.myBaseProject.realtime;

import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealtimeProviderRegistryTest {

    @Test
    void resolvesProviderByNormalizedName() {
        RealtimeInterviewProvider gemini = mock(RealtimeInterviewProvider.class);
        when(gemini.name()).thenReturn("gemini-live");
        RealtimeProviderRegistry registry = new RealtimeProviderRegistry(List.of(gemini));

        assertThat(registry.getProvider(" GEMINI-LIVE ")).isSameAs(gemini);
    }

    @Test
    void unknownProviderReturnsConfigurationError() {
        RealtimeProviderRegistry registry = new RealtimeProviderRegistry(List.of());

        assertThatThrownBy(() -> registry.getProvider("missing"))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.REALTIME_CONFIG_ERROR));
    }

    @Test
    void duplicateNormalizedNamesFailFast() {
        RealtimeInterviewProvider first = mock(RealtimeInterviewProvider.class);
        RealtimeInterviewProvider second = mock(RealtimeInterviewProvider.class);
        when(first.name()).thenReturn("gemini-live");
        when(second.name()).thenReturn(" GEMINI-LIVE ");

        assertThatThrownBy(() -> new RealtimeProviderRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class);
    }
}
