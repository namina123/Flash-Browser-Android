package com.oxgames.rufflewrapper.amf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Amf0Message {

    public static final int CURRENT_VERSION = 3;

    private int version = CURRENT_VERSION;
    private final List<Amf0Header> headers = new ArrayList<>();
    private final List<Amf0Body> bodies = new ArrayList<>();

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public List<Amf0Header> getHeaders() {
        return Collections.unmodifiableList(headers);
    }

    public List<Amf0Body> getBodies() {
        return Collections.unmodifiableList(bodies);
    }

    public void addHeader(Amf0Header header) {
        headers.add(header);
    }

    public void addHeader(String key, boolean required, Object value) {
        headers.add(new Amf0Header(key, required, value));
    }

    public void addBody(Amf0Body body) {
        bodies.add(body);
    }

    public void addBody(String target, String response, Object value) {
        bodies.add(new Amf0Body(target, response, value));
    }
}
