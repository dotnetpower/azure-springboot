package com.example.spring101.graphapi;

import java.util.Map;
import java.util.Optional;

public class HttpResponseWrapper {
    private final Map<String, String> headers;

    public HttpResponseWrapper(Map<String, String> headers) {
        this.headers = headers;
    }

    public int getHeaderAsInt(String headerName, int defaultValue) {
        return Optional.ofNullable(headers.get(headerName))
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }
}
