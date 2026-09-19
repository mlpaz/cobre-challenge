package com.cobre.notification.domain.model;

import java.util.List;

public record PagedResult<T>(List<T> items, long totalElements, int limit, int offset) {
}
