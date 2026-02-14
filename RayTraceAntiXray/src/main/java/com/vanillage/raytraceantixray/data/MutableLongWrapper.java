package com.vanillage.raytraceantixray.data;

public final class MutableLongWrapper extends LongWrapper {
    public MutableLongWrapper(final long value) {
        super(value);
    }

    public void setValue(final long value) {
        this.value = value;
    }
}
