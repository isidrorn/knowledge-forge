# knowledge-forge

> Automated pipeline that ingests technical articles, processes them with an LLM, and publishes structured Markdown — with a RAG query layer on the horizon.

Built as a portfolio project and a personal daily-use tool. The goal is a pragmatic, evolvable system: no premature abstractions, real production patterns from day one.

---

## What it does

1. **Ingests** articles from Gmail (IMAP) and HTTP REST endpoints
2. **Processes** each article through an LLM (Ollama local / OpenRouter) — generates a TL;DR, key points, tags, difficulty rating, and full Markdown
3. **Persists** everything to PostgreSQL with full status tracking
4. **Publishes** the resulting Markdown to a GitHub repository via the Contents API

---

## Architecture

Modular monolith. Clean package boundaries — `ingestion`, `processing`, `publisher` — designed to split into services if load ever justifies it (it doesn't yet).

```
Gmail / HTTP REST
       │
  [ingestion]  ── ArticleRaw ──▶  PostgreSQL
       │                               │
  Spring Events                        │
       │                               ▼
  [processing] ── LLM (Ollama / OpenRouter) ──▶ ArticleProcessed + OutboxEvents
                                                          │
                                                   [publisher]
                                                          │
                                              Markdown file  +  GitHub API
```

**Key patterns:**
- **Transactional Outbox** — `ArticleProcessed` and `OutboxEvents` are written in the same transaction; a scheduler drains and publishes, with retry up to N attempts
- **LLM provider chain** — OpenRouter as primary, Ollama as fallback; rate limiting via `Semaphore` + configurable delay
- **Idempotency** — SHA-256 checksum on raw content; `url` unique constraint
- **Status log** — append-only `pipeline_status_log` table for full state history

---

## Stack

| Layer | Technology |
|---|---|
| Runtime | Java 25, Spring Boot 4.0 |
| AI / LLM | Spring AI 2.0 (Ollama + OpenRouter) |
| Persistence | PostgreSQL 15, pgvector, Flyway, Spring Data JPA |
| ORM | Hibernate 7 |
| API docs | SpringDoc OpenAPI 3 |
| Observability | Spring Actuator, Micrometer |
| Infrastructure | Docker Compose |
| Testing | JUnit 5, Testcontainers, GreenMail |

---

## Data model

```
article_raw          article_processed       outbox_events
───────────          ─────────────────       ─────────────
id (PK)              id (PK)                 id (PK)
source               article_raw_id (FK)     article_processed_id (FK)
url (UNIQUE)         tldr                    event_type
content_checksum     key_points[]            payload (jsonb)
raw_content          tags[]                  status
status               difficulty (1-5)        attempts
retry_count          markdown_content        created_at
received_at          embedding vector(1536)  sent_at
processed_at         model_used
                     status
                     created_at / published_at

pipeline_status_log
───────────────────
id (bigserial)
article_raw_id (FK)
from_status / to_status
reason
changed_at
```

---

## Running locally

### Prerequisites

- Docker (for PostgreSQL + pgAdmin)
- [Ollama](https://ollama.com/) running locally with a model pulled (`qwen2.5-coder` by default)

### Start infrastructure

```bash
docker compose up -d
```

### Configure

Copy the example config and fill in your values:

```bash
cp src/main/resources/application.yml src/main/resources/application-local.yml
```

Required environment variables (or override in `application-local.yml`):

| Variable | Description |
|---|---|
| `OPENROUTER_API_KEY` | OpenRouter API key (optional — Ollama works without it) |
| `EMAIL_USER` | Gmail address for IMAP ingestion |
| `EMAIL_APP_PASSWORD` | Gmail App Password |
| `GITHUB_TOKEN` | PAT with `contents:write` on the target repo |
| `GITHUB_OWNER` / `GITHUB_REPO` | Target repository for published articles |

### Run

```bash
./mvnw spring-boot:run
```

OpenAPI UI available at `http://localhost:8080/swagger-ui.html`

---

## Roadmap

- [x] Ingestion — Gmail IMAP + HTTP REST
- [x] LLM processing — Ollama / OpenRouter with fallback chain
- [x] Publisher — Markdown local + GitHub Contents API
- [ ] RabbitMQ — decouple ingestion from processing
- [ ] RAG — pgvector + Spring AI vector store + query API
- [ ] Kubernetes manifests + Helm chart
- [ ] ArgoCD GitOps setup

---

## Architecture Decision Records

Notable decisions documented during development:

- **ADR-001** — PostgreSQL as single source of truth from day one (no FileWriter-only approach)
- **ADR-002** — RabbitMQ over Kafka (volume ≤100 articles/day, home lab context)
- **ADR-003** — Transactional Outbox for at-least-once publish guarantee
- **ADR-005** — Modular monolith before microservices
- **ADR-006** — `TOO_LARGE` articles are explicitly marked, never silently truncated

---

## License

MIT
