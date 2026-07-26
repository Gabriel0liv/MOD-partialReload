package com.gabriel0liv.partialreload.joint;

import java.time.Instant;

public record TagRecipeTransactionEvent(Instant at, TagRecipeTransactionStatus status, String detail) {}
