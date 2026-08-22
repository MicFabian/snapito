package io.github.micfabian.snapito.comparison;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
  private static final ObjectMapper MAPPER = JsonMapper.builder()
    .addModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
    .build();

  private Json() {
  }

  public static ObjectMapper mapper() {
    return MAPPER;
  }

  public static boolean isJson(String text) {
    if (text == null || text.isBlank()) {
      return false;
    }
    try {
      MAPPER.readTree(text);
      return true;
    } catch (JsonProcessingException e) {
      return false;
    }
  }

  public static Object parse(String text) {
    try {
      return toPlain(MAPPER.readTree(text));
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Unable to parse JSON: " + e.getOriginalMessage(), e);
    }
  }

  public static String write(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Unable to render JSON", e);
    }
  }

  public static String writePretty(Object value) {
    try {
      return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Unable to render JSON", e);
    }
  }

  public static Object toPlain(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return null;
    }
    if (node.isObject()) {
      Map<String, Object> map = new LinkedHashMap<>();
      ObjectNode object = (ObjectNode) node;
      object.fieldNames().forEachRemaining(name -> map.put(name, toPlain(object.get(name))));
      return map;
    }
    if (node.isArray()) {
      List<Object> list = new ArrayList<>();
      for (JsonNode item : (ArrayNode) node) {
        list.add(toPlain(item));
      }
      return list;
    }
    if (node.isBoolean()) {
      return node.booleanValue();
    }
    if (node.isNumber()) {
      return normalizeNumber(node.decimalValue());
    }
    return node.asText();
  }

  public static Object convert(Object value) {
    if (value == null) {
      return null;
    }
    return toPlain(MAPPER.valueToTree(value));
  }

  private static Object normalizeNumber(BigDecimal value) {
    BigDecimal stripped = value.stripTrailingZeros();
    return stripped.scale() <= 0 ? stripped.setScale(0) : stripped;
  }
}
