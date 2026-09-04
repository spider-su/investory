package com.smartbox.investory.integrations.management.scheduling;

import com.smartbox.investory.integrations.management.persistence.IntegrationInstanceEntity;
import com.smartbox.investory.integrations.management.persistence.IntegrationJobEntity;
import java.time.ZonedDateTime;

public record IntegrationJobContext(
    IntegrationInstanceEntity instance, IntegrationJobEntity job, ZonedDateTime now) {}
