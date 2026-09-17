package com.loopers.domain.common;

import java.util.List;
import java.util.function.Function;

/**
 * 목록 조회 결과. totalElements 는 목록의 기준 자원 수다.
 */
public record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResult<T> of(List<T> items, PageCommand command, long totalElements) {
        int totalPages = (int) ((totalElements + command.size() - 1) / command.size());
        return new PageResult<>(items, command.page(), command.size(), totalElements, totalPages);
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), page, size, totalElements, totalPages);
    }
}
