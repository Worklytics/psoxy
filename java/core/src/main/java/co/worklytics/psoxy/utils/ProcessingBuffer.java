package co.worklytics.psoxy.utils;

import co.worklytics.psoxy.storage.impl.ColumnarBulkDataSanitizerImpl;
import com.google.api.client.util.Lists;
import com.google.common.base.Preconditions;
import lombok.Getter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class ProcessingBuffer<T> {

    @Getter
    final int capacity;
    final Consumer<Collection<T>> consumer;

    // making it synchronized so can be used in parallel streams if wanted, but usually won't
    private final List<T> buffer = Collections.synchronizedList(Lists.newArrayList());
    @Getter
    private int flushes = 0;

    public ProcessingBuffer(int capacity, Consumer<Collection<T>> consumer) {
        Preconditions.checkArgument(capacity > 0);
        this.capacity = capacity;
        this.consumer = consumer;
    }


    public void addAndAttemptFlush(T t) {
        this.buffer.add(t);
        this.flushIfFull();
    }

    /**
     * Flushes if buffer has anything
     */
    public void flush() {
        if (!this.buffer.isEmpty()) {
            consumer.accept(this.buffer);
            flushes++;
            this.buffer.clear();
        }
    }

    /**
     * Flushes if buffer is full
     */
    private void flushIfFull() {
        if (this.buffer.size() >= capacity) {
            this.flush();
        }
    }
}
