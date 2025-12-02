package web;

import java.util.List;
import java.util.Map;

public record Message(Map<String, List<String>> content) {}

