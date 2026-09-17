package com.smartbox.investory.accounting;

import java.time.Instant;

/** Confirmation package returned by ZUS EWD for a previously submitted package. */
public record ZusDraConfirmation(
    String submissionId,
    String taskId,
    String type,
    Instant writtenAt,
    byte[] packagePayload,
    String packageHash,
    byte[] rawSoapResponse,
    Instant fetchedAt) {}
