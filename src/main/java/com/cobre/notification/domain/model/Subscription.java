package com.cobre.notification.domain.model;

public record Subscription(String userId, String eventType, String webHookUrl) {
}
