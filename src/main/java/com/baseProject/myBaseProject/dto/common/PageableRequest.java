package com.baseProject.myBaseProject.dto.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PageableRequest(
        @Min(value = 0, message = "page không được âm")
        Integer page,

        @Min(value = 1, message = "size tối thiểu là 1")
        @Max(value = 50, message = "size tối đa là 50")
        Integer size
) {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;

    public PageableRequest {
        if (page == null) page = DEFAULT_PAGE;
        if (size == null) size = DEFAULT_SIZE;
    }

    public int effectivePage() {
        return page;
    }

    public int effectiveSize() {
        return size;
    }
}
