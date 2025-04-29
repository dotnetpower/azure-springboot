package com.example.spring101;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.example.spring101.graphapi.GraphApiResponse;
import com.example.spring101.graphapi.GraphClientFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

@SpringBootApplication
public class Spring101Application implements CommandLineRunner {

	public static Logger logger = Logger.getLogger(Spring101Application.class.getName());

	public static void main(String[] args) {
		logger.info("Starting Spring Boot application...");
		System.setProperty("AZURE_LOG_LEVEL", "body_and_headers");

		SpringApplication.run(Spring101Application.class, args);
		logger.info("Spring Boot application started successfully.");
	}

	@Override
	public void run(String... args) throws Exception {

		// Load properties file and set properties used throughout the sample
        Properties properties = new Properties();
        properties.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("application.properties"));

      
		GraphClientFactory factory = new GraphClientFactory(
            properties.getProperty("TENANT_ID"),
            properties.getProperty("CLIENT_ID"),
            properties.getProperty("SECRET"),
            "https://login.microsoftonline.com"
        );

		// 병렬 실행을 위한 스레드 풀 생성
		ExecutorService executorService = Executors.newFixedThreadPool(100); // 10개의 스레드 사용
		
		AtomicReference<List<String>> results = new AtomicReference<>(new ArrayList<>());

		AtomicBoolean shouldStop = new AtomicBoolean(false);
		AtomicInteger threadId = new AtomicInteger(0);

		CompletableFuture<?>[] asyncFutures = IntStream.range(0, 1900)
			.mapToObj(x -> CompletableFuture.runAsync(() -> {
				try {				

					if (shouldStop.get()) return;

					threadId.set(x);
					
					GraphApiResponse response = factory.callGraphApiWithHeaders("sites/root", Optional.of(x));
					if (response == null || formatJson(response.getHeaders()).contains("HTTP/1.1 429")) {
						shouldStop.set(true);
						
						// throw new RuntimeException("Throttling detected or response is null");
					}
					
					// List<String> headers = Arrays.asList(formatJson(response.getHeaders()).split("\n"));
			
					// int maxLines = Math.max(results.get().size(), headers.size());

					// for (int i = 0; i < maxLines; i++) {
					// 	String prevLine = i < results.get().size() ? results.get().get(i) : "";
					// 	String currLine = i < headers.size() ? headers.get(i) : "";

					// 	if(!prevLine.equals(currLine)) {
					// 		// System.out.println(currLine);
					// 	}
					// }
						
					// results.set(headers);

					System.out.println("Thread ID: " + x);	

				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}, executorService))
			.toArray(CompletableFuture[]::new);

		try {
			CompletableFuture.allOf(asyncFutures).join();
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Error occurred: {0}", e.getMessage());
		}
		//executorService.shutdown();

		System.out.println("@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");

		// 순차적으로 다시 실행
		IntStream.range(0, 5000).forEach(x -> {
			try {
				threadId.set(threadId.get() + 1);
				GraphApiResponse response = factory.callGraphApiWithHeaders("sites/root", Optional.of((int)threadId.get()));
				// if (response == null || formatJson(response.getHeaders()).contains("HTTP/1.1 429")) {
				// 	throw new RuntimeException("Throttling detected or response is null");
				// }

				System.out.println("Thread ID: " + threadId.get());

			} catch (Exception e) {
				logger.log(Level.SEVERE, "Error occurred: {0}", e.getMessage());
				throw new RuntimeException(e);
			}
		});


		logger.info("All tasks completed.");
	}

	String formatJson(String json) {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		JsonElement jsonElement = com.google.gson.JsonParser.parseString(json);
		return gson.toJson(jsonElement);
	}

	String formatJson(Map<String, List<String>> headers) {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		
		return gson.toJson(headers);
	}

}
