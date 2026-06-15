package com.example.demo.services.imports;

public record ImportRowErrorResponse(Long id,
                                     String sheetName,
                                     Integer rowNumber,
                                     String errorCode,
                                     String errorMessage) {
}

