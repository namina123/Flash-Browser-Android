package com.oxgames.rufflewrapper.amf;

public class Amf0Header {

    private String key;
    private boolean required;
    private Object value;

    public Amf0Header(String key, boolean required, Object value) {
        this.key = key;
        this.required = required;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public boolean isRequired() {
        return required;
    }

    public Object getValue() {
        return value;
    }
}
