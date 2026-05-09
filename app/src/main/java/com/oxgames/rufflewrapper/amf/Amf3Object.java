package com.oxgames.rufflewrapper.amf;

/**
 * Wraps an AMF3 payload when transporting it inside an AMF0 envelope.
 */
public final class Amf3Object {

    private final Object value;

    public Amf3Object(Object value) {
        this.value = value;
    }

    public Object getValue() {
        return value;
    }
}
