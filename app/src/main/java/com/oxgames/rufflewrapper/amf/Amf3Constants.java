package com.oxgames.rufflewrapper.amf;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

interface Amf3Constants {
    Charset UTF8 = StandardCharsets.UTF_8;

    int AMF3_UNDEFINED = 0x00;
    int AMF3_NULL = 0x01;
    int AMF3_BOOLEAN_FALSE = 0x02;
    int AMF3_BOOLEAN_TRUE = 0x03;
    int AMF3_INTEGER = 0x04;
    int AMF3_NUMBER = 0x05;
    int AMF3_STRING = 0x06;
    int AMF3_XML_DOCUMENT = 0x07;
    int AMF3_DATE = 0x08;
    int AMF3_ARRAY = 0x09;
    int AMF3_OBJECT = 0x0A;
    int AMF3_XML = 0x0B;
    int AMF3_BYTE_ARRAY = 0x0C;
    int AMF3_VECTOR_INT = 0x0D;
    int AMF3_VECTOR_UINT = 0x0E;
    int AMF3_VECTOR_NUMBER = 0x0F;
    int AMF3_VECTOR_OBJECT = 0x10;
    int AMF3_DICTIONARY = 0x11;
}
