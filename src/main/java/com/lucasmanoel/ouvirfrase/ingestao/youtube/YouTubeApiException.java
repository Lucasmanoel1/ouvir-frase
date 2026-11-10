package com.lucasmanoel.ouvirfrase.ingestao.youtube;

public class YouTubeApiException extends RuntimeException {
    public YouTubeApiException(String message) {
        super(message);
    }
    public YouTubeApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
