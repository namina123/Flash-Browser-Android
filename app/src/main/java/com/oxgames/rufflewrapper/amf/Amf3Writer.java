package com.oxgames.rufflewrapper.amf;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class Amf3Writer implements Amf3Constants {

    private static final int AMF3_MIN_INT = -268435456;
    private static final int AMF3_MAX_INT = 268435455;
    private static final String ARRAY_COLLECTION_ALIAS = "flex.messaging.io.ArrayCollection";

    private final OutputStream out;
    private final Map<String, Integer> stringReferences = new LinkedHashMap<>();
    private final IdentityHashMap<Object, Integer> objectReferences = new IdentityHashMap<>();
    private final Map<TraitDescriptor, Integer> traitReferences = new LinkedHashMap<>();

    Amf3Writer(OutputStream out) {
        this.out = out;
    }

    void writeObject(Object value) throws IOException {
        if (value == null) {
            out.write(AMF3_NULL);
            return;
        }
        if (value == AmfUndefined.INSTANCE) {
            out.write(AMF3_UNDEFINED);
            return;
        }
        if (value instanceof Boolean) {
            out.write(Boolean.TRUE.equals(value) ? AMF3_BOOLEAN_TRUE : AMF3_BOOLEAN_FALSE);
            return;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
            int intValue = ((Number) value).intValue();
            if (intValue >= AMF3_MIN_INT && intValue <= AMF3_MAX_INT) {
                out.write(AMF3_INTEGER);
                writeU29(intValue & 0x1FFFFFFF);
            } else {
                out.write(AMF3_NUMBER);
                writeDouble(((Number) value).doubleValue());
            }
            return;
        }
        if (value instanceof Long) {
            long longValue = ((Long) value).longValue();
            if (longValue >= AMF3_MIN_INT && longValue <= AMF3_MAX_INT) {
                out.write(AMF3_INTEGER);
                writeU29((int) longValue & 0x1FFFFFFF);
            } else {
                out.write(AMF3_NUMBER);
                writeDouble((double) longValue);
            }
            return;
        }
        if (value instanceof Float || value instanceof Double) {
            out.write(AMF3_NUMBER);
            writeDouble(((Number) value).doubleValue());
            return;
        }
        if (value instanceof Character || value instanceof String) {
            out.write(AMF3_STRING);
            writeStringData(value.toString());
            return;
        }
        if (value instanceof Date) {
            writeDate((Date) value);
            return;
        }
        if (value instanceof byte[]) {
            writeByteArray((byte[]) value);
            return;
        }
        if (value instanceof ArrayCollection) {
            writeArrayCollection((ArrayCollection) value);
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
        if (value instanceof AsObject) {
            writeDynamicObject((AsObject) value);
            return;
        }
        if (value instanceof Map) {
            writeDynamicMap((Map<?, ?>) value);
            return;
        }
        throw new IOException("Unsupported AMF3 type: " + value.getClass().getName());
    }

    private void writeDate(Date value) throws IOException {
        out.write(AMF3_DATE);
        Integer reference = objectReferences.get(value);
        if (reference != null) {
            writeU29(reference.intValue() << 1);
            return;
        }
        objectReferences.put(value, objectReferences.size());
        writeU29(0x01);
        writeDouble(value.getTime());
    }

    private void writeByteArray(byte[] value) throws IOException {
        out.write(AMF3_BYTE_ARRAY);
        Integer reference = objectReferences.get(value);
        if (reference != null) {
            writeU29(reference.intValue() << 1);
            return;
        }
        objectReferences.put(value, objectReferences.size());
        writeU29((value.length << 1) | 0x01);
        out.write(value);
    }

    private void writeArrayCollection(ArrayCollection value) throws IOException {
        out.write(AMF3_OBJECT);
        Integer reference = objectReferences.get(value);
        if (reference != null) {
            writeU29(reference.intValue() << 1);
            return;
        }
        objectReferences.put(value, objectReferences.size());
        writeTrait(new TraitDescriptor(ARRAY_COLLECTION_ALIAS, false, true, new ArrayList<String>()));
        writeDenseCollection(new ArrayList<Object>(value));
    }

    private void writeAmfArray(AmfArray value) throws IOException {
        out.write(AMF3_ARRAY);
        Integer reference = objectReferences.get(value);
        if (reference != null) {
            writeU29(reference.intValue() << 1);
            return;
        }
        objectReferences.put(value, objectReferences.size());
        writeU29((value.size() << 1) | 0x01);
        for (Map.Entry<String, Object> entry : value.getAssociativeValues().entrySet()) {
            if (entry.getKey() != null && entry.getKey().length() > 0) {
                writeStringData(entry.getKey());
                writeObject(entry.getValue());
            }
        }
        writeStringData("");
        for (Object item : value) {
            writeObject(item);
        }
    }

    private void writeDenseCollection(Collection<?> value) throws IOException {
        out.write(AMF3_ARRAY);
        Integer reference = objectReferences.get(value);
        if (reference != null) {
            writeU29(reference.intValue() << 1);
            return;
        }
        objectReferences.put(value, objectReferences.size());
        writeU29((value.size() << 1) | 0x01);
        writeStringData("");
        for (Object item : value) {
            writeObject(item);
        }
    }

    private void writeDenseArray(Object array) throws IOException {
        out.write(AMF3_ARRAY);
        Integer reference = objectReferences.get(array);
        if (reference != null) {
            writeU29(reference.intValue() << 1);
            return;
        }
        objectReferences.put(array, objectReferences.size());
        int length = Array.getLength(array);
        writeU29((length << 1) | 0x01);
        writeStringData("");
        for (int i = 0; i < length; i++) {
            writeObject(Array.get(array, i));
        }
    }

    private void writeDynamicObject(AsObject value) throws IOException {
        out.write(AMF3_OBJECT);
        Integer reference = objectReferences.get(value);
        if (reference != null) {
            writeU29(reference.intValue() << 1);
            return;
        }
        objectReferences.put(value, objectReferences.size());
        writeTrait(new TraitDescriptor(emptyToBlank(value.getType()), true, false, new ArrayList<String>()));
        writeDynamicEntries(value);
    }

    private void writeDynamicMap(Map<?, ?> value) throws IOException {
        out.write(AMF3_OBJECT);
        Integer reference = objectReferences.get(value);
        if (reference != null) {
            writeU29(reference.intValue() << 1);
            return;
        }
        objectReferences.put(value, objectReferences.size());
        writeTrait(new TraitDescriptor("", true, false, new ArrayList<String>()));
        writeDynamicEntries(value);
    }

    private void writeDynamicEntries(Map<?, ?> value) throws IOException {
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            Object rawKey = entry.getKey();
            if (rawKey == null) {
                continue;
            }
            String key = rawKey.toString();
            if (key.length() == 0) {
                continue;
            }
            writeStringData(key);
            writeObject(entry.getValue());
        }
        writeStringData("");
    }

    private void writeTrait(TraitDescriptor trait) throws IOException {
        Integer reference = traitReferences.get(trait);
        if (reference != null) {
            writeU29((reference.intValue() << 2) | 0x01);
            return;
        }
        traitReferences.put(trait, traitReferences.size());
        int flags = (trait.sealedNames.size() << 4) | 0x03;
        if (trait.externalizable) {
            flags |= 0x04;
        }
        if (trait.dynamic) {
            flags |= 0x08;
        }
        writeU29(flags);
        writeStringData(trait.className);
        for (String sealedName : trait.sealedNames) {
            writeStringData(sealedName);
        }
    }

    private void writeStringData(String value) throws IOException {
        String normalized = emptyToBlank(value);
        if (normalized.length() == 0) {
            writeU29(0x01);
            return;
        }
        Integer reference = stringReferences.get(normalized);
        if (reference != null) {
            writeU29(reference.intValue() << 1);
            return;
        }
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
        stringReferences.put(normalized, stringReferences.size());
        writeU29((bytes.length << 1) | 0x01);
        out.write(bytes);
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

    private void writeU29(int value) throws IOException {
        if (value < 0x80) {
            out.write(value);
        } else if (value < 0x4000) {
            out.write(((value >> 7) & 0x7F) | 0x80);
            out.write(value & 0x7F);
        } else if (value < 0x200000) {
            out.write(((value >> 14) & 0x7F) | 0x80);
            out.write(((value >> 7) & 0x7F) | 0x80);
            out.write(value & 0x7F);
        } else {
            out.write(((value >> 22) & 0x7F) | 0x80);
            out.write(((value >> 15) & 0x7F) | 0x80);
            out.write(((value >> 8) & 0x7F) | 0x80);
            out.write(value & 0xFF);
        }
    }

    private static String emptyToBlank(String value) {
        return value == null ? "" : value;
    }

    private static final class TraitDescriptor {
        final String className;
        final boolean dynamic;
        final boolean externalizable;
        final List<String> sealedNames;

        TraitDescriptor(String className, boolean dynamic, boolean externalizable, List<String> sealedNames) {
            this.className = className;
            this.dynamic = dynamic;
            this.externalizable = externalizable;
            this.sealedNames = sealedNames;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TraitDescriptor)) {
                return false;
            }
            TraitDescriptor that = (TraitDescriptor) other;
            return dynamic == that.dynamic
                    && externalizable == that.externalizable
                    && Objects.equals(className, that.className)
                    && Objects.equals(sealedNames, that.sealedNames);
        }

        @Override
        public int hashCode() {
            return Objects.hash(className, dynamic, externalizable, sealedNames);
        }
    }
}
