package com.example.spring101.graphapi;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class GraphClientFactory {

    private final String tenantId;
    private final String clientId;
    private final String clientSecret;
    private final String authority;

    private final AtomicInteger throttleLimit = new AtomicInteger(0);
    private int reqCount = 0;

    private Map<Integer, Map<String, Integer>> rateLimitInfos = new HashMap<>();

    public GraphClientFactory(String tenantId, String clientId, String clientSecret, String authority) {
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.authority = authority;
    }

    /**
     * Get an access token from Azure AD.
     */
    public String getAccessToken() throws Exception {
        String tokenEndpoint = String.format("%s/%s/oauth2/v2.0/token", authority, tenantId);

        URL url = new URL(tokenEndpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        String body = String.format(
            "client_id=%s&scope=https://graph.microsoft.com/.default&client_secret=%s&grant_type=client_credentials",
            clientId, clientSecret
        );

        try (OutputStream os = connection.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        if (connection.getResponseCode() == 200) {
            // Parse the response to extract the access token
            String response = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return parseAccessToken(response);
        } else {
            throw new RuntimeException("Failed to get access token: " + connection.getResponseCode());
        }
    }

    /**
     * Call Microsoft Graph API with the access token and return both headers and body.
     */
    public GraphApiResponse callGraphApiWithHeaders(String endpoint, Optional<Integer> threadId) throws Exception {

        
        String accessToken = getAccessToken();

        // GET
        // URL url = new URL("https://graph.microsoft.com/v1.0/" + endpoint);
        // HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        // connection.setRequestMethod("GET");
        // connection.setRequestProperty("Authorization", "Bearer " + accessToken);

        // POST
        URL url = new URL("https://graph.microsoft.com/v1.0/$batch");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        String jsonBody = """
                          {
                            "requests": [
                                {
                                    "id": "1",
                                    "method": "GET",
                                    "url": "/sites/root"
                                },
                                {
                                    "id": "2",
                                    "dependsOn": [ "1" ],
                                    "method": "GET",
                                    "url": "/sites/root"
                                },
                                {
                                    "id": "3",
                                    "dependsOn": [ "2" ],
                                    "method": "GET",
                                    "url": "/sites/root"
                                },
                                {
                                    "id": "4",
                                    "dependsOn": [ "3" ],
                                    "method": "GET",
                                    "url": "/sites/root"
                                },
                                {
                                    "id": "5",
                                    "dependsOn": [ "4" ],
                                    "method": "GET",
                                    "url": "/sites/root"
                                },
                                {
                                    "id": "6",
                                    "dependsOn": [ "5" ],
                                    "method": "GET",
                                    "url": "/sites/root"
                                },
                                {
                                    "id": "7",
                                    "dependsOn": [ "6" ],
                                    "method": "GET",
                                    "url": "/sites/root"
                                },
                                {
                                    "id": "8",
                                    "dependsOn": [ "7" ],
                                    "method": "GET",
                                    "url": "/sites/root"
                                },
                                {
                                    "id": "9",
                                    "dependsOn": [ "8" ],
                                    "method": "GET",
                                    "url": "/sites/root"
                                },
                                {
                                    "id": "10",
                                    "dependsOn": [ "9" ],
                                    "method": "GET",
                                    "url": "/sites/root"
                                }
                            ]
                          }""";
        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        // connection.setRequestProperty("Content-Type", "application/json");




        reqCount++;

        int retries = 0;
        int backoffTime = 1;  // 초기 대기 시간 (1초)
        int maxBackoffTime = 60;  // 최대 대기 시간 (60초)

        while (retries < 5) {  // 최대 재시도 횟수 (5번)

            // 헤더에서 RateLimit 관련 값 추출
            int retryAfter = getRetryAfter(connection);
            int rateLimitLimit = getRateLimitLimit(connection);
            int rateLimitReset = getRateLimitReset(connection);
            int rateLimitRemaining = getRateLimitRemaining(connection);

            if(retryAfter > 0){
                System.out.println("==============================================");
                System.out.println("Thread ID: " + threadId.orElse(-1));
                System.out.println(url.toString());
                System.out.println("Retry-After: " + retryAfter);
            }

            if(rateLimitLimit > 1){
                System.out.println("==============================================");
                System.out.println("Thread ID: " + threadId.orElse(-1));
                System.out.println(url.toString());
                // System.out.println("Retry-After: " + retryAfter);
                System.out.println("RateLimit-Limit: " + rateLimitLimit);
                System.out.println("RateLimit-Remaining: " + rateLimitRemaining);
                System.out.println("RateLimit-Reset: " + rateLimitReset);

                double rateLimitPercentage = 100 - ((double) rateLimitRemaining / rateLimitLimit) * 100;
                System.out.println("RateLimit-Percentage: " + rateLimitPercentage + "%");

                // 기존에 있던 데이터 가져오기 (없으면 새로 만들기)
                Map<String, Integer> rateLimitInfo = rateLimitInfos.getOrDefault(threadId, new HashMap<>());

                // 데이터 설정 또는 업데이트
                rateLimitInfo.put("timestamp", (int) Instant.now().getEpochSecond());
                rateLimitInfo.put("rateLimitLimit", rateLimitLimit);
                rateLimitInfo.put("rateLimitReset", rateLimitReset);
                rateLimitInfo.put("rateLimitRemaining", rateLimitRemaining);
                rateLimitInfo.put("reqCount", reqCount);

                // 업데이트된 맵을 다시 저장
                rateLimitInfos.put(threadId.orElse(-1), rateLimitInfo);

                return null;

                // throttleLimit.set((int) rateLimitPercentage);
            }

            // RateLimit-Remaining이 20(프로세스 수)이면 대기 후 재시도
            if (rateLimitRemaining > 0 && rateLimitRemaining <= 20) {
                System.out.println("Rate limit exceeded. Waiting for " + rateLimitReset + " seconds...");
                waitForRateLimitReset(rateLimitReset);  // 지정된 시간 동안 대기

                // 재시도
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                reqCount++;

                retries++;
                backoffTime = Math.min(backoffTime * 2, maxBackoffTime);  // 지수적으로 증가 (최대값 60초)
            } else {
                break;  // Rate limit 초과가 아니면 루프 종료
            }
        }

        if (retries == 5) {
            System.err.println("Exceeded maximum retries for waiting on rate limit reset.");
            return null;  
        }

        if (connection.getResponseCode() == 200) {
            System.out.println("Response 200 Total: " + reqCount);
            String responseBody = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // Post 일때는 항상 200 으로 옴
            if (responseBody.contains(":429")){
                System.out.println("Response Body: " + responseBody);
                printRateLimit(rateLimitInfos);
                return null;
            }

            Map<String, List<String>> responseHeaders = connection.getHeaderFields();
            return new GraphApiResponse(responseHeaders, responseBody);
        } else {
            System.err.println("Failed to call Graph API: " + connection.getResponseCode());
            Map<String, List<String>> responseHeaders = connection.getHeaderFields();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            System.out.println("Thread ID: " + threadId.orElse(-1));
            System.err.println("Response Headers: " + gson.toJson(responseHeaders));

            printRateLimit(rateLimitInfos);
            
            return null;
        }
    }

    private void printRateLimit(Map<Integer, Map<String, Integer>> rateLimitInfos) {
        System.out.println("//////////////////////////////////////////////////////"); 
        rateLimitInfos.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach((entry) -> {
            int id = entry.getKey();
            System.out.println("{");
            System.out.println("  \"threadId\": " + id + ",");
            System.out.println("  \"rateLimitInfo\": {");

            Map<String, Integer> rateLimitInfo = entry.getValue();
            rateLimitInfo.forEach((key, value) -> {
                System.out.printf("    \"%s\": %d,%n", key, value);
            });
            System.out.println("  }");
            System.out.println("}");
        });
    }

    // Retry-After 값 추출: 대기 시간(초)
    private int getRetryAfter(HttpURLConnection connection) {
        String retryAfterValue = getHeaderValue(connection, "Retry-After");
        return Integer.parseInt(retryAfterValue);
    }

    // RateLimit-Limit 값 추출: 해당 API 가 허용하는 총 요청수
    private int getRateLimitLimit(HttpURLConnection connection) {
        String limitValue = getHeaderValue(connection, "RateLimit-Limit");
        return Integer.parseInt(limitValue);
    }

    // RateLimit-Reset 값 추출: 다음 요청 가능 시간(초)
    private int getRateLimitReset(HttpURLConnection connection) {
        String resetValue = getHeaderValue(connection, "RateLimit-Reset");
        return Integer.parseInt(resetValue);
    }

    // RateLimit-Remaining 값 추출 : 현재 남은 요청 수
    private int getRateLimitRemaining(HttpURLConnection connection) {
        String remainingValue = getHeaderValue(connection, "RateLimit-Remaining");
        return Integer.parseInt(remainingValue);
    }

    // 헤더에서 특정 값 추출
    private String getHeaderValue(HttpURLConnection connection, String headerName) {
        Map<String, List<String>> headers = connection.getHeaderFields();
        List<String> values = headers.get(headerName);

        if(headers.size() > 10){
            
            if (values != null && !values.isEmpty()) {
                return values.get(0);  // 첫 번째 값 반환 (일반적으로 하나의 값만 있음)
            }
        }
        return "-1";  // 값이 없으면 기본값 -1 반환(null 로 간주)
    }

    // RateLimit-Reset 값에 맞춰 대기하는 함수
    private void waitForRateLimitReset(int resetTimeInSeconds) {
        try {
            System.out.println("Waiting for " + resetTimeInSeconds + " seconds before retrying...");
            TimeUnit.SECONDS.sleep(resetTimeInSeconds);  // 설정된 시간 동안 대기
            System.out.println("Wait time over. You can now retry.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Error while waiting for rate limit reset.");
        }
    }

    public Optional<String> callGraphApi(String endpoint) throws Exception {
        String accessToken = getAccessToken();

        URL url = new URL("https://graph.microsoft.com/v1.0/" + endpoint);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);

        if (connection.getResponseCode() == 200) {
            String response = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return Optional.of(response);
        } else {
            System.err.println("Failed to call Graph API: " + connection.getResponseCode());
            return Optional.empty();
        }
    }

    /**
     * Parse the access token from the JSON response.
     */
    private String parseAccessToken(String jsonResponse) {
        // Simple parsing logic (use a JSON library like Jackson or Gson for production code)
        int startIndex = jsonResponse.indexOf("\"access_token\":\"") + 16;
        int endIndex = jsonResponse.indexOf("\"", startIndex);
        return jsonResponse.substring(startIndex, endIndex);
    }
}
