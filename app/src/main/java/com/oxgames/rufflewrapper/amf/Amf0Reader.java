package com.oxgames.rufflewrapper.amf;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

final class Amf0Reader {

    private final InputStream in;
    private final List<Object> references = new ArrayList<>();

    Amf0Reader(InputStream in) {
        this.in = in;
    }

    Amf0Message readMessage() throws IOException {
        Amf0Message message = new Amf0Message();
        message.setVersion(readUnsignedShort());
        int headerCount = readUnsignedShort();
        for (int i = 0; i < headerCount; i++) {
            references.clear();
            String key = readUtf();
            boolean required = readUnsignedByte() != 0;
            readInt();
            message.addHeader(key, required, readValue());
        }
        int bodyCount = readUnsignedShort();
        for (int i = 0; i < bodyCount; i++) {
            references.clear();
            String target = readUtf();
            String response = readUtf();
            readInt();
            message.addBody(new Amf0Body(target, response, readValue()));
        }
        return message;
    }

    Object readValue() throws IOException {
        int type = readUnsignedByte();
        switch (type) {
            case Amf0Body.DATA_TYPE_NUMBER:
                return Double.valueOf(readDouble());
            case Amf0Body.DATA_TYPE_BOOLEAN:
                return Boolean.valueOf(readUnsignedByte() != 0);
            case Amf0Body.DATA_TYPE_STRING:
                return readUtf();
            case Amf0Body.DATA_TYPE_OBJECT:
                return readObject(null);
            case Amf0Body.DATA_TYPE_NULL:
                return null;
            case Amf0Body.DATA_TYPE_UNDEFINED:
                return AmfUndefined.INSTANCE;
            case Amf0Body.DATA_TYPE_REFERENCE_OBJECT:
                return references.get(readUnsignedShort());
            case Amf0Body.DATA_TYPE_MIXED_ARRAY:
                return readEcmaArray();
            case Amf0Body.DATA_TYPE_ARRAY:
                return readStrictArray();
            case Amf0Body.DATA_TYPE_DATE:
                Date date = new Date((long) readDouble());
                readUnsignedShort();
                return date;
            case Amf0Body.DATA_TYPE_LONG_STRING:
                return readLongUtf();
            case Amf0Body.DATA_TYPE_XML:
                return readLongUtf();
            case Amf0Body.DATA_TYPE_CUSTOM_CLASS:
                return readObject(readUtf());
            case Amf0Body.DATA_TYPE_AMF3_OBJECT:
                return new Amf3Object(new Amf3Reader(in).readObject());
            default:
                throw new IOException("Unsupported AMF0 marker: " + type);
        }
    }

    private AsObject readObject(String type) throws IOException {
        AsObject result = new AsObject(type);
        references.add(result);
        while (true) {
            String key = readUtf();
            int valueType = readUnsignedByte();
            if (key.length() == 0 && valueType == Amf0Body.DATA_TYPE_OBJECT_END) {
                break;
            }
            result.put(key, readValueForKnownType(valueType));
        }
        return result;
    }

    private AmfArray readEcmaArray() throws IOException {
        readInt();
        AmfArray result = new AmfArray();
        references.add(result);
        while (true) {
            String key = readUtf();
            int valueType = readUnsignedByte();
            if (key.length() == 0 && valueType == Amf0Body.DATA_TYPE_OBJECT_END) {
                break;
            }
            Object value = readValueForKnownType(valueType);
            if (isArrayIndex(key)) {
                int index = Integer.parseInt(key);
                while (result.size() <= index) {
                    result.add(null);
                }
                result.set(index, value);
            } else {
                result.getAssociativeValues().put(key, value);
            }
        }
        return result;
    }

    private AmfArray readStrictArray() throws IOException {
        int length = readInt();
        AmfArray result = new AmfArray();
        references.add(result);
        for (int i = 0; i < length; i++) {
            result.add(readValue());
        }
        return result;
    }

    private Object readValueForKnownType(int type) throws IOException {
        switch (type) {
            case Amf0Body.DATA_TYPE_NUMBER:
                return Double.valueOf(readDouble());
            case Amf0Body.DATA_TYPE_BOOLEAN:
                return Boolean.valueOf(readUnsignedByte() != 0);
            case Amf0Body.DATA_TYPE_STRING:
                return readUtf();
            case Amf0Body.DATA_TYPE_OBJECT:
                return readObject(null);
            case Amf0Body.DATA_TYPE_NULL:
                return null;
            case Amf0Body.DATA_TYPE_UNDEFINED:
                return AmfUndefined.INSTANCE;
            case Amf0Body.DATA_TYPE_REFERENCE_OBJECT:
                return references.get(readUnsignedShort());
            case Amf0Body.DATA_TYPE_MIXED_ARRAY:
                return readEcmaArray();
            case Amf0Body.DATA_TYPE_ARRAY:
                return readStrictArray();
            case Amf0Body.DATA_TYPE_DATE:
                Date date = new Date((long) readDouble());
                readUnsignedShort();
                return date;
            case Amf0Body.DATA_TYPE_LONG_STRING:
                return readLongUtf();
            case Amf0Body.DATA_TYPE_XML:
                return readLongUtf();
            case Amf0Body.DATA_TYPE_CUSTOM_CLASS:
                return readObject(readUtf());
            case Amf0Body.DATA_TYPE_AMF3_OBJECT:
                return new Amf3Object(new Amf3Reader(in).readObject());
            default:
                throw new IOException("Unsupported AMF0 marker: " + type);
        }
    }

    private String readUtf() throws IOException {
        return new String(readBytes(readUnsignedShort()), StandardCharsets.UTF_8);
    }

    private String readLongUtf() throws IOException {
        return new String(readBytes(readInt()), StandardCharsets.UTF_8);
    }

    private byte[] readBytes(int length) throws IOException {
        byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(result, offset, length - offset);
            if (read < 0) {
                throw new EOFException("Unexpected end of AMF stream");
            }
            offset += read;
        }
        return result;
    }

    private int readUnsignedByte() throws IOException {
        int value = in.read();
        if (value < 0) {
            throw new EOFException("Unexpected end of AMF stream");
        }
        return value;
    }

    private int readUnsignedShort() throws IOException {
        return (readUnsignedByte() << 8) | readUnsignedByte();
    }

    private int readInt() throws IOException {
        return (readUnsignedByte() << 24)
                | (readUnsignedByte() << 16)
                | (readUnsignedByte() << 8)
                | readUnsignedByte();
    }

    private double readDouble() throws IOException {
        long bits = 0L;
        for (int i = 0; i < 8; i++) {
            bits = (bits << 8) | readUnsignedByte();
        }
        return Double.longBitsToDouble(bits);
    }

    private static boolean isArrayIndex(String value) {
        if (value == null || value.length() == 0) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
