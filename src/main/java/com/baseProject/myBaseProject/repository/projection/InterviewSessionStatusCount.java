package com.baseProject.myBaseProject.repository.projection;

import com.baseProject.myBaseProject.enums.InterviewSessionStatus;

public interface InterviewSessionStatusCount {
    InterviewSessionStatus getStatus();

    long getTotal();
}
