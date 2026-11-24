package io.github.micfabian.snapito.spring;

import io.github.micfabian.snapito.Snapito;
import io.github.micfabian.snapito.comparison.Json;
import io.github.micfabian.snapito.comparison.JsonComparison;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

public final class MvcSnapshots {
  private static final List<String> DEFAULT_HEADERS =
    List.of("Content-Type", "Location", "Content-Disposition", "Cache-Control");

  private MvcSnapshots() {
  }

  public static void expectResponse(MvcResult result) {
    expectResponse(result, DEFAULT_HEADERS, json -> {
    });
  }

  public static void expectResponse(MvcResult result, Consumer<JsonComparison> configuration) {
    expectResponse(result, DEFAULT_HEADERS, configuration);
  }

  public static void expectResponse(
      MvcResult result,
      List<String> headerNames,
      Consumer<JsonComparison> configuration) {
    Snapito.expectJson(response(result.getResponse(), headerNames), configuration);
  }

  public static Map<String, Object> response(MockHttpServletResponse response) {
    return response(response, DEFAULT_HEADERS);
  }

  public static Map<String, Object> response(MockHttpServletResponse response, List<String> headerNames) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("status", response.getStatus());
    Map<String, Object> headers = new LinkedHashMap<>();
    for (String name : headerNames) {
      List<String> values = response.getHeaders(name);
      if (values.isEmpty()) {
        continue;
      }
      headers.put(name, values.size() == 1 ? values.get(0) : new ArrayList<>(values));
    }
    if (!headers.isEmpty()) {
      snapshot.put("headers", headers);
    }
    String body = body(response);
    if (!body.isEmpty()) {
      snapshot.put("body", isJson(response) && Json.isJson(body) ? Json.parse(body) : body);
    }
    return snapshot;
  }

  private static boolean isJson(MockHttpServletResponse response) {
    String contentType = response.getContentType();
    return contentType != null && contentType.toLowerCase(java.util.Locale.ROOT).contains("json");
  }

  private static String body(MockHttpServletResponse response) {
    try {
      return response.getContentAsString();
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("Unable to read the response body", e);
    }
  }
}
