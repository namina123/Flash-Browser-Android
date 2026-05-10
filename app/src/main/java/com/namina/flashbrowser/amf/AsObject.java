package com.namina.flashbrowser.amf;

import java.util.LinkedHashMap;

/**
 * Dynamic ActionScript-style object with an optional remote class alias.
 */
public class AsObject extends LinkedHashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    private String type;

    public AsObject() {
        super();
    }

    public AsObject(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
