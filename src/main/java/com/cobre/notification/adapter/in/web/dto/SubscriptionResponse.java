package com.cobre.notification.adapter.in.web.dto;

public record SubscriptionResponse(String userId, String eventType, String webHookUrl) {
}
