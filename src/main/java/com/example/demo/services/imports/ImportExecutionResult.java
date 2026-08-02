package com.example.demo.services.imports;

public record ImportExecutionResult(int rowsTotal, int rowsApplied, int rowsFailed, String details) {}

