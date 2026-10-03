# Market Radar module

Experimental market-intelligence POC hosted inside Investory to reuse the existing runtime while the
concept is validated. The module is intentionally designed so it can later be extracted into a
separate application.

## Scope of this skeleton

The first boundary models research signals only:

- price/volume observations,
- media/analyst observations,
- consensus snapshots,
- portfolio relevance supplied through a port.

It does not make trade decisions and does not execute orders.

## Boundaries

- `api`: small public read/application contract exposed to Investory.
- `application`: orchestration only.
- `domain`: framework-free radar vocabulary.
- `port`: interfaces for market/media/portfolio inputs that can later move behind external adapters.

The module must not depend on Investment persistence, REST controllers, MVC controllers, Thymeleaf,
JPA entities, or implementation services. Portfolio integration should implement
`PortfolioContextPort` outside this module rather than introducing direct persistence coupling.

## POC constraints

Keep the first implementation deliberately small. Do not add compatibility layers, in-process REST
clients, page-model factories, assemblers, formatter layers, schedulers, persistence, or provider SDKs
until a concrete use case requires them.
