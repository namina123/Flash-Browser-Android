package com.oxgames.rufflewrapper.amf;

public class Amf0Body {

    public static final byte DATA_TYPE_UNKNOWN = -1;
    public static final byte DATA_TYPE_NUMBER = 0;
    public static final byte DATA_TYPE_BOOLEAN = 1;
    public static final byte DATA_TYPE_STRING = 2;
    public static final byte DATA_TYPE_OBJECT = 3;
    public static final byte DATA_TYPE_NULL = 5;
    public static final byte DATA_TYPE_UNDEFINED = 6;
    public static final byte DATA_TYPE_REFERENCE_OBJECT = 7;
    public static final byte DATA_TYPE_MIXED_ARRAY = 8;
    public static final byte DATA_TYPE_OBJECT_END = 9;
    public static final byte DATA_TYPE_ARRAY = 10;
    public static final byte DATA_TYPE_DATE = 11;
    public static final byte DATA_TYPE_LONG_STRING = 12;
    public static final byte DATA_TYPE_AS_OBJECT = 13;
    public static final byte DATA_TYPE_RECORDSET = 14;
    public static final byte DATA_TYPE_XML = 15;
    public static final byte DATA_TYPE_CUSTOM_CLASS = 16;
    public static final byte DATA_TYPE_AMF3_OBJECT = 17;

    private final String target;
    private final String response;
    private final Object value;
    private final byte type;

    public Amf0Body(String target, String response, Object value) {
        this(target, response, value, DATA_TYPE_UNKNOWN);
    }

    public Amf0Body(String target, String response, Object value, byte type) {
        this.target = target;
        this.response = response;
        this.value = value;
        this.type = type;
    }

    public String getTarget() {
        return target;
    }

    public String getResponse() {
        return response;
    }

    public Object getValue() {
        return value;
    }

    public byte getType() {
        return type;
    }
}
