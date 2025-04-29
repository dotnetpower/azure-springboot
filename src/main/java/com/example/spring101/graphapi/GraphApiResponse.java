package com.example.spring101.graphapi;

import java.util.List;
import java.util.Map;

/**
 * A simple class to hold the response headers and body.
 */
public class GraphApiResponse {
    private final Map<String, List<String>> headers;
    private final String body;

    public GraphApiResponse(Map<String, List<String>> headers, String body) {
        this.headers = headers;
        this.body = body;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }
}