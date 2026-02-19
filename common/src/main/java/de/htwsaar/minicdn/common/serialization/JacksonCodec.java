package de.htwsaar.minicdn.common.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class JacksonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        // register the module
        MAPPER.registerModule(new JavaTimeModule());
    }

    private JacksonCodec() {
        // Utility
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new MiniCdnSerializationException("Failed to serialize object to the JSON format !", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new MiniCdnSerializationException(
                    "Failed to deserialize JSON format to : [" + clazz.getSimpleName() + "]", e);
        }
    }
}
