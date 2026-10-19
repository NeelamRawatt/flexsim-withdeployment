package com.example.experiment_service.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * One page of a list (for the history screen).
 *
 * We do not send Spring's own Page object. Its JSON shape can change between Spring versions.
 * This small box has a fixed shape that we control. The names are the same as the old answer
 * (content, totalElements, totalPages, number, size, first, last, numberOfElements, empty).
 *
 * A "record" is a short way to write an unchangeable data box. Java makes the constructor and readers.
 */
public record PageResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int number,
        int size,
        boolean first,
        boolean last,
        int numberOfElements,
        boolean empty) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize(),
                page.isFirst(),
                page.isLast(),
                page.getNumberOfElements(),
                page.isEmpty());
    }
}