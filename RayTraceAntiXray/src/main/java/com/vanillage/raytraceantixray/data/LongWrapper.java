package com.vanillage.raytraceantixray.data;

public class LongWrapper {
    protected long value;

    public LongWrapper(final long value) {
        this.value = value;
    }

    public final long getValue() {
        return this.value;
    }

    @Override
    public final boolean equals(final Object obj) {
        return this == obj || obj instanceof LongWrapper && this.value == ((LongWrapper) obj).value;
    }

    @Override
    public final int hashCode() {
        return Long.hashCode(this.value);
    }
}
