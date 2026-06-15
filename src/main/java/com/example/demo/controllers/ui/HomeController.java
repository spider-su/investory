package com.example.demo.controllers.ui;

import com.example.demo.services.BenchmarkService;
import com.example.demo.services.PortfolioService;
import com.example.demo.services.models.Portfolio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final PortfolioService portfolioService;
    private final BenchmarkService benchmarkService;

    @GetMapping("/")
    public String home() {
        return "home";
    }

//    @GetMapping("/user")
//    @ResponseBody
//    public OAuth2User user(@AuthenticationPrincipal OAuth2User principal) {
//        return principal;
//    }

    @GetMapping("/dashboard")
    public String getPortfolioDashboard(Model model) {
        Portfolio stats = portfolioService.calculateTotalProfitLoss(); // assuming this returns your full JSON
        model.addAttribute("stats", stats);
        model.addAttribute("benchmark", benchmarkService.calculate());
        return "dashboard";
    }
}
