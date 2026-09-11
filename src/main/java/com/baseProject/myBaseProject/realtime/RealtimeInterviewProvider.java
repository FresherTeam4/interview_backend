package com.baseProject.myBaseProject.realtime;

import java.util.Set;

public interface RealtimeInterviewProvider {
    String name();

    RealtimeProviderCapabilities capabilities();

    String defaultVoice();

    Set<String> supportedVoices();

    RealtimeSessionGrant createSession(RealtimeSessionSpec specification);

    RealtimeSessionGrant resumeSession(
            RealtimeSessionSpec specification, String resumptionHandle);
}
