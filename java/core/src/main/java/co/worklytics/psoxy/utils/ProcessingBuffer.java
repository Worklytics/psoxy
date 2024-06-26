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
    private long processed = 0;

    public ProcessingBuffer(int capacity, Consumer<Collection<T>> consumer) {
        Preconditions.checkArgument(capacity > 0);
        this.capacity = capacity;
        this.consumer = consumer;
    }


    public boolean addAndAttemptFlush(T t) {
        this.buffer.add(t);
        return this.flushIfFull();
    }

    /**
     * Flushes if buffer has anything
     */
    public boolean flush() {
        if (!this.buffer.isEmpty()) {
            consumer.accept(this.buffer);
            processed+=this.buffer.size();
            this.buffer.clear();
            return true;
        }
        return false;
    }

    /**
     * Flushes if buffer is full
     */
    private boolean flushIfFull() {
        if (this.buffer.size() >= capacity) {
            return this.flush();
        }
        return false;
    }
}
