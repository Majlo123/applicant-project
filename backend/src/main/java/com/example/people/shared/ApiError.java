package com.example.people.shared;

import java.util.Map;

public record ApiError(String code, String message, Map<String, String> fieldErrors) {}
