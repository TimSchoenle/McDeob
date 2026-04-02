package com.shanebeestudios.mcdeop.processor;

import java.util.Optional;
import org.jetbrains.annotations.Nullable;

final class ProcessorStatusReporter {
    @Nullable private final ResponseConsumer responseConsumer;

    ProcessorStatusReporter(@Nullable final ResponseConsumer responseConsumer) {
        this.responseConsumer = responseConsumer;
    }

    void send(final String statusMessage) {
        Optional.ofNullable(this.responseConsumer).ifPresent(consumer -> consumer.onStatusUpdate(statusMessage));
    }
}
