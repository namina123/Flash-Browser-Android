package com.oxgames.rufflewrapper.amf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Lightweight AMF0/AMF3 codec block adapted for the Android app.
 *
 * <p>Supported today:
 * null, undefined, booleans, numbers, strings, dates, byte arrays,
 * dense/associative arrays, dynamic objects, typed dynamic objects,
 * Flex ArrayCollection, and AMF0 envelopes carrying AMF3 payloads.</p>
 *
 * <p>Not included yet:
 * Granite bean mapping, custom externalizers beyond ArrayCollection/ObjectProxy,
 * and arbitrary Java object serialization.</p>
 */
public final class AmfCodec {

    private AmfCodec() {
    }

    public static byte[] encodeAmf3(Object value) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new Amf3Writer(out).writeObject(value);
        return out.toByteArray();
    }

    public static Object decodeAmf3(byte[] bytes) throws IOException {
        return new Amf3Reader(new ByteArrayInputStream(bytes)).readObject();
    }

    public static byte[] encodeAmf0Message(Amf0Message message) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new Amf0Writer(out).writeMessage(message);
        return out.toByteArray();
    }

    public static Amf0Message decodeAmf0Message(byte[] bytes) throws IOException {
        return new Amf0Reader(new ByteArrayInputStream(bytes)).readMessage();
    }
}
