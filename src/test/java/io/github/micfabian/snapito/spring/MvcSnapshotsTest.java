package io.github.micfabian.snapito.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.micfabian.snapito.comparison.Json;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class MvcSnapshotsTest {
  @Test
  void capturesStatusSelectedHeadersAndParsedJsonBody() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(201);
    response.setContentType("application/json");
    response.setHeader("Location", "/orders/7");
    response.setHeader("Set-Cookie", "session=abc");
    response.getWriter().write("{\"id\":7,\"tags\":[\"a\"]}");

    Map<String, Object> snapshot = MvcSnapshots.response(response);

    assertEquals(201, snapshot.get("status"));
    assertEquals(
      Map.of("Content-Type", "application/json", "Location", "/orders/7"),
      snapshot.get("headers"));
    assertEquals(Json.parse("{\"id\":7,\"tags\":[\"a\"]}"), snapshot.get("body"));
  }

  @Test
  void keepsNonJsonBodiesAsTextAndHonoursCustomHeaders() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(200);
    response.setContentType("text/plain");
    response.setHeader("X-Trace", "t-1");
    response.getWriter().write("hello");

    Map<String, Object> snapshot = MvcSnapshots.response(response, List.of("X-Trace"));

    assertEquals(Map.of("X-Trace", "t-1"), snapshot.get("headers"));
    assertEquals("hello", snapshot.get("body"));
  }

  @Test
  void omitsHeadersAndBodyWhenThereAreNone() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(204);

    assertEquals(Map.of("status", 204), MvcSnapshots.response(response));
  }
}
