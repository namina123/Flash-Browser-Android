package com.namina.flashbrowser.amf;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Amf3Reader implements Amf3Constants {

    private static final String ARRAY_COLLECTION_ALIAS = "flex.messaging.io.ArrayCollection";
    private static final String OBJECT_PROXY_ALIAS = "flex.messaging.io.ObjectProxy";

    private final InputStream in;
    private final List<String> stringReferences = new ArrayList<>();
    private final List<Object> objectReferences = new ArrayList<>();
    private final List<TraitDescriptor> traitReferences = new ArrayList<>();

    Amf3Reader(InputStream in) {
        this.in = in;
    }

    Object readObject() throws IOException {
        int type = readUnsignedByte();
        switch (type) {
            case AMF3_UNDEFINED:
                return AmfUndefined.INSTANCE;
            case AMF3_NULL:
                return null;
            case AMF3_BOOLEAN_FALSE:
                return Boolean.FALSE;
            case AMF3_BOOLEAN_TRUE:
                return Boolean.TRUE;
            case AMF3_INTEGER:
                return Integer.valueOf(readInteger());
            case AMF3_NUMBER:
                return Double.valueOf(readDouble());
            case AMF3_STRING:
                return readStringData();
            case AMF3_DATE:
                return readDate();
            case AMF3_ARRAY:
                return readArray();
            case AMF3_OBJECT:
                return readTypedObject();
            case AMF3_BYTE_ARRAY:
                return readByteArray();
            case AMF3_XML:
            case AMF3_XML_DOCUMENT:
                return readXmlString();
            case AMF3_VECTOR_INT:
                return readVectorInt();
            case AMF3_VECTOR_UINT:
                return readVectorUInt();
            case AMF3_VECTOR_NUMBER:
                return readVectorNumber();
            case AMF3_VECTOR_OBJECT:
                return readVectorObject();
            case AMF3_DICTIONARY:
                return readDictionary();
            default:
                throw new IOException("Unsupported AMF3 marker: " + type);
        }
    }

    private int readInteger() throws IOException {
        int value = readU29();
        if ((value & 0x10000000) != 0) {
            value -= 0x20000000;
        }
        return value;
    }

    private double readDouble() throws IOException {
        long bits = 0L;
        for (int i = 0; i < 8; i++) {
            bits = (bits << 8) | readUnsignedByte();
        }
        return Double.longBitsToDouble(bits);
    }

    private String readStringData() throws IOException {
        int type = readU29();
        if ((type & 0x01) == 0) {
            return stringReferences.get(type >> 1);
        }
        int length = type >> 1;
        if (length == 0) {
            return "";
        }
        byte[] bytes = readBytes(length);
        String value = new String(bytes, StandardCharsets.UTF_8);
        stringReferences.add(value);
        return value;
    }

    private Object readDate() throws IOException {
        int type = readU29();
        if ((type & 0x01) == 0) {
            return objectReferences.get(type >> 1);
        }
        Date result = new Date((long) readDouble());
        objectReferences.add(result);
        return result;
    }

    private Object readArray() throws IOException {
        int type = readU29();
        if ((type & 0x01) == 0) {
            return objectReferences.get(type >> 1);
        }
        int denseLength = type >> 1;
        AmfArray result = new AmfArray();
        objectReferences.add(result);
        while (true) {
            String key = readStringData();
            if (key.length() == 0) {
                break;
            }
            result.getAssociativeValues().put(key, readObject());
        }
        for (int i = 0; i < denseLength; i++) {
            result.add(readObject());
        }
        return result;
    }

    private Object readTypedObject() throws IOException {
        int type = readU29();
        if ((type & 0x01) == 0) {
            return objectReferences.get(type >> 1);
        }

        TraitDescriptor trait = readTrait(type);
        if (trait.externalizable) {
            if (ARRAY_COLLECTION_ALIAS.equals(trait.className)) {
                ArrayCollection result = new ArrayCollection();
                objectReferences.add(result);
                Object values = readObject();
                if (values instanceof Iterable) {
                    for (Object value : (Iterable<?>) values) {
                        result.add(value);
                    }
                } else if (values != null && values.getClass().isArray()) {
                    int length = java.lang.reflect.Array.getLength(values);
                    for (int i = 0; i < length; i++) {
                        result.add(java.lang.reflect.Array.get(values, i));
                    }
                } else if (values != null) {
                    result.add(values);
                }
                return result;
            }
            if (OBJECT_PROXY_ALIAS.equals(trait.className)) {
                int index = objectReferences.size();
                objectReferences.add(null);
                Object proxyValue = readObject();
                objectReferences.set(index, proxyValue);
                return proxyValue;
            }
            throw new IOException("Unsupported AMF3 externalizable alias: " + trait.className);
        }

        AsObject result = new AsObject(trait.className.length() == 0 ? null : trait.className);
        objectReferences.add(result);
        for (String sealedName : trait.sealedNames) {
            result.put(sealedName, readObject());
        }
        if (trait.dynamic) {
            while (true) {
                String key = readStringData();
                if (key.length() == 0) {
                    break;
                }
                result.put(key, readObject());
            }
        }
        return result;
    }

    private TraitDescriptor readTrait(int type) throws IOException {
        if ((type & 0x02) == 0) {
            return traitReferences.get(type >> 2);
        }
        int sealedCount = type >> 4;
        boolean externalizable = (type & 0x04) != 0;
        boolean dynamic = (type & 0x08) != 0;
        String className = readStringData();
        List<String> sealedNames = new ArrayList<>(sealedCount);
        for (int i = 0; i < sealedCount; i++) {
            sealedNames.add(readStringData());
        }
        TraitDescriptor result = new TraitDescriptor(className, dynamic, externalizable, sealedNames);
        traitReferences.add(result);
        return result;
    }

    private byte[] readByteArray() throws IOException {
        int type = readU29();
        if ((type & 0x01) == 0) {
            return (byte[]) objectReferences.get(type >> 1);
        }
        int length = type >> 1;
        byte[] result = readBytes(length);
        objectReferences.add(result);
        return result;
    }

    private String readXmlString() throws IOException {
        int type = readU29();
        if ((type & 0x01) == 0) {
            return (String) objectReferences.get(type >> 1);
        }
        int length = type >> 1;
        String result = new String(readBytes(length), StandardCharsets.UTF_8);
        objectReferences.add(result);
        return result;
    }

    private List<Integer> readVectorInt() throws IOException {
        int type = readU29();
        if ((type & 0x01) == 0) {
            return (List<Integer>) objectReferences.get(type >> 1);
        }
        int length = type >> 1;
        readUnsignedByte();
        List<Integer> result = new ArrayList<>(length);
        objectReferences.add(result);
        for (int i = 0; i < length; i++) {
            int value = (readUnsignedByte() << 24)
                    | (readUnsignedByte() << 16)
                    | (readUnsignedByte() << 8)
                    | readUnsignedByte();
            result.add(Integer.valueOf(value));
        }
        return result;
    }

    private List<Long> readVectorUInt() throws IOException {
        int type = readU29();
        if ((type & 0x01) == 0) {
            return (List<Long>) objectReferences.get(type >> 1);
        }
        int length = type >> 1;
        readUnsignedByte();
        List<Long> result = new ArrayList<>(length);
        objectReferences.add(result);
        for (int i = 0; i < length; i++) {
            long value = ((long) readUnsignedByte() << 24)
                    | ((long) readUnsignedByte() << 16)
                    | ((long) readUnsignedByte() << 8)
                    | readUnsignedByte();
            result.add(Long.valueOf(value & 0xFFFFFFFFL));
        }
        return result;
    }

    private List<Double> readVectorNumber() throws IOException {
        int type = readU29();
        if ((type & 0x01) == 0) {
            return (List<Double>) objectReferences.get(type >> 1);
        }
        int length = type >> 1;
        readUnsignedByte();
        List<Double> result = new ArrayList<>(length);
        objectReferences.add(result);
        for (int i = 0; i < length; i++) {
            result.add(Double.valueOf(readDouble()));
        }
        return result;
    }

    private List<Object> readVectorObject() throws IOException {
        int type = readU29();
        if ((type & 0x01) == 0) {
            return (List<Object>) objectReferences.get(type >> 1);
        }
        int length = type >> 1;
        readUnsignedByte();
        readStringData();
        List<Object> result = new ArrayList<>(length);
        objectReferences.add(result);
        for (int i = 0; i < length; i++) {
            result.add(readObject());
        }
        return result;
    }

    private Map<Object, Object> readDictionary() throws IOException {
        int type = readU29();
        if ((type & 0x01) == 0) {
            return (Map<Object, Object>) objectReferences.get(type >> 1);
        }
        int length = type >> 1;
        readUnsignedByte();
        Map<Object, Object> result = new LinkedHashMap<>();
        objectReferences.add(result);
        for (int i = 0; i < length; i++) {
            result.put(readObject(), readObject());
        }
        return result;
    }

    private byte[] readBytes(int length) throws IOException {
        byte[] bytes = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(bytes, offset, length - offset);
            if (read < 0) {
                throw new EOFException("Unexpected end of AMF stream");
            }
            offset += read;
        }
        return bytes;
    }

    private int readU29() throws IOException {
        int b = readUnsignedByte();
        if (b < 128) {
            return b;
        }
        int value = (b & 0x7F) << 7;
        b = readUnsignedByte();
        if (b < 128) {
            return value | b;
        }
        value = (value | (b & 0x7F)) << 7;
        b = readUnsignedByte();
        if (b < 128) {
            return value | b;
        }
        value = (value | (b & 0x7F)) << 8;
        return value | readUnsignedByte();
    }

    private int readUnsignedByte() throws IOException {
        int value = in.read();
        if (value < 0) {
            throw new EOFException("Unexpected end of AMF stream");
        }
        return value;
    }

    private static final class TraitDescriptor {
        final String className;
        final boolean dynamic;
        final boolean externalizable;
        final List<String> sealedNames;

        TraitDescriptor(String className, boolean dynamic, boolean externalizable, List<String> sealedNames) {
            this.className = className == null ? "" : className;
            this.dynamic = dynamic;
            this.externalizable = externalizable;
            this.sealedNames = sealedNames;
        }
    }
}
