package com.baseProject.myBaseProject.realtime;

import com.baseProject.myBaseProject.realtime.model.RealtimeSessionGrant;
import com.baseProject.myBaseProject.realtime.model.RealtimeSessionSpec;

public interface ResumableRealtimeProvider extends RealtimeInterviewProvider {
    RealtimeSessionGrant resumeSession(
            RealtimeSessionSpec specification, String resumptionHandle);
}
