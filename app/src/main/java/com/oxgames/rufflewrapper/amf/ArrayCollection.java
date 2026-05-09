package com.oxgames.rufflewrapper.amf;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Minimal Flex ArrayCollection representation.
 */
public class ArrayCollection extends ArrayList<Object> {

    private static final long serialVersionUID = 1L;

    public ArrayCollection() {
        super();
    }

    public ArrayCollection(Collection<?> values) {
        super(values == null ? 0 : values.size());
        if (values != null) {
            addAll(values);
        }
    }

    public ArrayCollection(Object[] values) {
        if (values != null) {
            for (Object value : values) {
                add(value);
            }
        }
    }
}
