package com.baseProject.myBaseProject.jd.extraction;

public interface JobDescriptionTextExtractor {

    JobDescriptionFileType fileType();

    String extract(byte[] content);
}
