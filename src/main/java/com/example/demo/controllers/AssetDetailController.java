package com.example.demo.controllers;

import com.example.demo.services.dashboard.AssetDetailNotFoundException;
import com.example.demo.services.dashboard.AssetDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;

@Controller
@RequiredArgsConstructor
public class AssetDetailController {

  private final AssetDetailService assetDetailService;

  @GetMapping("/dashboard/assets/{symbol}")
  public String detail(@PathVariable String symbol, Model model) {
    model.addAttribute("asset", assetDetailService.findBySymbol(symbol));
    return "dashboard/asset-detail";
  }

  @ExceptionHandler(AssetDetailNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String notFound(AssetDetailNotFoundException exception, Model model) {
    model.addAttribute("message", exception.getMessage());
    return "dashboard/asset-not-found";
  }
}
