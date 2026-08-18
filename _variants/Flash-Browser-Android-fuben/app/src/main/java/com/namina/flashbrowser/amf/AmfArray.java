package com.namina.flashbrowser.amf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AMF array that may contain both dense items and associative entries.
 */
public class AmfArray extends ArrayList<Object> {

    private static final long serialVersionUID = 1L;

    private final LinkedHashMap<String, Object> associativeValues = new LinkedHashMap<>();

    public Map<String, Object> getAssociativeValues() {
        return associativeValues;
    }

    public boolean hasAssociativeValues() {
        return !associativeValues.isEmpty();
    }
}
