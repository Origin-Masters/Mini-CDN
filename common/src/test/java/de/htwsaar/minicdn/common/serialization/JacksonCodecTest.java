package de.htwsaar.minicdn.common.serialization;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import de.htwsaar.minicdn.common.dto.RegisterNodeDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class JacksonCodecTest {

    @Test
    void testToJson() {
        RegisterNodeDto dto = new RegisterNodeDto("EU", "http://localhost:8081");
        String json = JacksonCodec.toJson(dto);

        assertNotNull(json);
        assertTrue(json.contains("\"region\":\"EU\""));
        assertTrue(json.contains("\"url\":\"http://localhost:8081\""));
    }

    @Test
    void testFromJson() {

        String json = "{\"region\":\"US\",\"url\":\"http://gruppe-8-minicdn.com\"}";
        RegisterNodeDto dto = JacksonCodec.fromJson(json, RegisterNodeDto.class);

        assertNotNull(dto);
        assertEquals("US", dto.getRegion());
        assertEquals("http://gruppe-8-minicdn.com", dto.getUrl());
    }

    @Test
    void testFromJson_InvalidJson_ThrowsException() {
        // Kein gültiges JSON
        String invalidJson = "{region: kaputt}";
        assertThrows(MiniCdnSerializationException.class, () -> {
            JacksonCodec.fromJson(invalidJson, RegisterNodeDto.class);
        });
    }

    @Test
    void testFromJson_TypeReference() {
        String json =
                """
                [
                  {"region":"EU","url":"http://localhost:8081"},
                  {"region":"US","url":"http://localhost:8082"}
                ]
                """;

        List<RegisterNodeDto> dtos = JacksonCodec.fromJson(json, new TypeReference<>() {});

        assertEquals(2, dtos.size());
        assertEquals("EU", dtos.get(0).getRegion());
        assertEquals("http://localhost:8082", dtos.get(1).getUrl());
    }
}
