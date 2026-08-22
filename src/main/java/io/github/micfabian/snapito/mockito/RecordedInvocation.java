package io.github.micfabian.snapito.mockito;

import java.util.List;
import java.util.Map;

public class RecordedInvocation {
  private String mock;
  private String method;
  private List<Object> arguments;
  private Object returnValue;
  private String thrown;
  private Integer sequence;
  private long globalSequence;

  public RecordedInvocation() {
  }

  public long getGlobalSequence() {
    return globalSequence;
  }

  public void setGlobalSequence(long globalSequence) {
    this.globalSequence = globalSequence;
  }

  public String getMock() {
    return mock;
  }

  public void setMock(String mock) {
    this.mock = mock;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public List<Object> getArguments() {
    return arguments;
  }

  public void setArguments(List<Object> arguments) {
    this.arguments = arguments;
  }

  public Object getReturnValue() {
    return returnValue;
  }

  public void setReturnValue(Object returnValue) {
    this.returnValue = returnValue;
  }

  public String getThrown() {
    return thrown;
  }

  public void setThrown(String thrown) {
    this.thrown = thrown;
  }

  public Integer getSequence() {
    return sequence;
  }

  public void setSequence(Integer sequence) {
    this.sequence = sequence;
  }

  public Map<String, Object> asMap() {
    java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
    if (sequence != null) {
      map.put("sequence", sequence);
    }
    map.put("mock", mock);
    map.put("method", method);
    map.put("arguments", arguments);
    if (returnValue != null) {
      map.put("returnValue", returnValue);
    }
    if (thrown != null) {
      map.put("thrown", thrown);
    }
    return map;
  }
}
