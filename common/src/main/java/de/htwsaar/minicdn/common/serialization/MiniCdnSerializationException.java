package de.htwsaar.minicdn.common.serialization;

public class MiniCdnSerializationException extends RuntimeException {

    public MiniCdnSerializationException(String message) {

        super(message);
    }

    public MiniCdnSerializationException(String message, Throwable cause) {

        super(message, cause);
    }
}
