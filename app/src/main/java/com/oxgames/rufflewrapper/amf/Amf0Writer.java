package com.oxgames.rufflewrapper.amf;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Map;

final class Amf0Writer {

    private final OutputStream out;
    private final IdentityHashMap<Object, Integer> references = new IdentityHashMap<>();

    Amf0Writer(OutputStream out) {
        this.out = out;
    }

    void writeMessage(Amf0Message message) throws IOException {
        writeUnsignedShort(message.getVersion());
        writeUnsignedShort(message.getHeaders().size());
        for (Amf0Header header : message.getHeaders()) {
            writeUtf(header.getKey());
            out.write(header.isRequired() ? 1 : 0);
            writeInt(0);
            writeValue(header.getValue());
        }
        writeUnsignedShort(message.getBodies().size());
        for (Amf0Body body : message.getBodies()) {
            writeUtf(body.getTarget());
            writeUtf(body.getResponse());
            writeInt(0);
            writeValue(body.getValue());
        }
    }

    void writeValue(Object value) throws IOException {
        if (value == null) {
            out.write(Amf0Body.DATA_TYPE_NULL);
            return;
        }
        if (value == AmfUndefined.INSTANCE) {
            out.write(Amf0Body.DATA_TYPE_UNDEFINED);
            return;
        }
        if (value instanceof Amf3Object) {
            out.write(Amf0Body.DATA_TYPE_AMF3_OBJECT);
            new Amf3Writer(out).writeObject(((Amf3Object) value).getValue());
            return;
        }
        if (value instanceof Boolean) {
            out.write(Amf0Body.DATA_TYPE_BOOLEAN);
            out.write(Boolean.TRUE.equals(value) ? 1 : 0);
            return;
        }
        if (value instanceof Number) {
            out.write(Amf0Body.DATA_TYPE_NUMBER);
            writeDouble(((Number) value).doubleValue());
            return;
        }
        if (value instanceof Character || value instanceof String) {
            writeStringValue(value.toString());
            return;
        }
        if (value instanceof Date) {
            out.write(Amf0Body.DATA_TYPE_DATE);
            writeDouble(((Date) value).getTime());
            writeUnsignedShort(0);
            return;
        }
        if (value instanceof AsObject) {
            writeAsObject((AsObject) value);
            return;
        }
        if (value instanceof Map) {
            writeAnonymousObject((Map<?, ?>) value);
            return;
        }
        if (value instanceof AmfArray) {
            writeAmfArray((AmfArray) value);
            return;
        }
        if (value instanceof Collection) {
            writeDenseCollection((Collection<?>) value);
            return;
        }
        if (value.getClass().isArray()) {
            writeDenseArray(value);
            return;
        }
        throw new IOException("Unsupported AMF0 type: " + value.getClass().getName());
    }

    private void writeAsObject(AsObject value) throws IOException {
        Integer reference = references.get(value);
        if (reference != null) {
            out.write(Amf0Body.DATA_TYPE_REFERENCE_OBJECT);
            writeUnsignedShort(reference.intValue());
            return;
        }
        references.put(value, references.size());
        String type = value.getType();
        if (type != null && type.length() > 0) {
            out.write(Amf0Body.DATA_TYPE_CUSTOM_CLASS);
            writeUtf(type);
        } else {
            out.write(Amf0Body.DATA_TYPE_OBJECT);
        }
        writeObjectEntries(value);
    }

    private void writeAnonymousObject(Map<?, ?> value) throws IOException {
        Integer reference = references.get(value);
        if (reference != null) {
            out.write(Amf0Body.DATA_TYPE_REFERENCE_OBJECT);
            writeUnsignedShort(reference.intValue());
            return;
        }
        references.put(value, references.size());
        out.write(Amf0Body.DATA_TYPE_OBJECT);
        writeObjectEntries(value);
    }

    private void writeObjectEntries(Map<?, ?> value) throws IOException {
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            writeUtf(entry.getKey().toString());
            writeValue(entry.getValue());
        }
        writeUnsignedShort(0);
        out.write(Amf0Body.DATA_TYPE_OBJECT_END);
    }

    private void writeAmfArray(AmfArray value) throws IOException {
        Integer reference = references.get(value);
        if (reference != null) {
            out.write(Amf0Body.DATA_TYPE_REFERENCE_OBJECT);
            writeUnsignedShort(reference.intValue());
            return;
        }
        references.put(value, references.size());
        if (value.hasAssociativeValues()) {
            out.write(Amf0Body.DATA_TYPE_MIXED_ARRAY);
            writeInt(value.size());
            for (Map.Entry<String, Object> entry : value.getAssociativeValues().entrySet()) {
                writeUtf(entry.getKey());
                writeValue(entry.getValue());
            }
            for (int i = 0; i < value.size(); i++) {
                writeUtf(Integer.toString(i));
                writeValue(value.get(i));
            }
            writeUnsignedShort(0);
            out.write(Amf0Body.DATA_TYPE_OBJECT_END);
        } else {
            out.write(Amf0Body.DATA_TYPE_ARRAY);
            writeInt(value.size());
            for (Object item : value) {
                writeValue(item);
            }
        }
    }

    private void writeDenseCollection(Collection<?> value) throws IOException {
        Integer reference = references.get(value);
        if (reference != null) {
            out.write(Amf0Body.DATA_TYPE_REFERENCE_OBJECT);
            writeUnsignedShort(reference.intValue());
            return;
        }
        references.put(value, references.size());
        out.write(Amf0Body.DATA_TYPE_ARRAY);
        writeInt(value.size());
        for (Object item : value) {
            writeValue(item);
        }
    }

    private void writeDenseArray(Object value) throws IOException {
        Integer reference = references.get(value);
        if (reference != null) {
            out.write(Amf0Body.DATA_TYPE_REFERENCE_OBJECT);
            writeUnsignedShort(reference.intValue());
            return;
        }
        references.put(value, references.size());
        out.write(Amf0Body.DATA_TYPE_ARRAY);
        int length = Array.getLength(value);
        writeInt(length);
        for (int i = 0; i < length; i++) {
            writeValue(Array.get(value, i));
        }
    }

    private void writeStringValue(String value) throws IOException {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 65536) {
            out.write(Amf0Body.DATA_TYPE_STRING);
            writeUnsignedShort(bytes.length);
        } else {
            out.write(Amf0Body.DATA_TYPE_LONG_STRING);
            writeInt(bytes.length);
        }
        out.write(bytes);
    }

    private void writeUtf(String value) throws IOException {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        writeUnsignedShort(bytes.length);
        out.write(bytes);
    }

    private void writeUnsignedShort(int value) throws IOException {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private void writeInt(int value) throws IOException {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private void writeDouble(double value) throws IOException {
        long bits = Double.doubleToLongBits(value);
        out.write((int) ((bits >>> 56) & 0xFF));
        out.write((int) ((bits >>> 48) & 0xFF));
        out.write((int) ((bits >>> 40) & 0xFF));
        out.write((int) ((bits >>> 32) & 0xFF));
        out.write((int) ((bits >>> 24) & 0xFF));
        out.write((int) ((bits >>> 16) & 0xFF));
        out.write((int) ((bits >>> 8) & 0xFF));
        out.write((int) (bits & 0xFF));
    }
}
