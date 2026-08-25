# UI visual regression

The responsive baseline uses the Profile page because it exercises the shared planning header,
summary metrics, composition cards, allocation chart, table, and mobile overflow behavior in one
stable route.

| Baseline | Viewport |
| --- | --- |
| `ui-baselines/profile-desktop.png` | 1920 × 1080 |
| `ui-baselines/profile-laptop.png` | 1440 × 900 |
| `ui-baselines/profile-tablet.png` | 1024 × 768 |
| `ui-baselines/profile-mobile.png` | 390 × 844 |

Refresh these files only after reviewing an intentional layout change. Use the same local portfolio,
dark theme, browser engine, and `/investment-profile?portfolioId=1` route. The baseline contract test
guards the viewport matrix and prevents missing or empty reference files.
