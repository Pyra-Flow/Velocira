# Velocira local development

Docker Desktop is the supported local runtime. The repository-root
`compose.yml` starts the complete application: PostgreSQL, Qdrant, the AI
service, the Spring API, and the Next.js frontend.

## Start the complete stack

1. Install and start Docker Desktop.
2. Copy `.env.example` to `.env`, then replace the three required secret
   placeholders: `POSTGRES_PASSWORD`, `JWT_SECRET`, and
   `AI_SERVICE_SHARED_SECRET`.
3. From the repository root, run:

   ```powershell
   docker compose up --build --wait
   ```

Open <http://localhost:3000>. Docker waits until each service is healthy. The
first backend image build downloads Maven dependencies and can take several
minutes on a new machine; following builds reuse Docker's build cache.

## Useful commands

```powershell
# See service health and published ports
docker compose ps

# Follow all service logs
docker compose logs --follow

# Stop services while retaining database and Qdrant data
docker compose down

# Stop services and remove local Docker volumes (deletes local database/vector data)
docker compose down --volumes
```

The published defaults are frontend `3000`, API `8080`, AI service `8000`,
PostgreSQL `5432`, and Qdrant `6333`/`6334`. Change them in `.env` if they
conflict with another local service.

Always run Compose from the repository root so the single `compose.yml` file
and its matching `.env` are used together.

## Default project flow

The first-use path is intentionally brief: create a project, describe the idea,
and select **Generate project**. The server derives a title and sensible project
defaults, records a canonical brief, and starts a durable background project-plan
job. The workspace translates worker stages into plain-language progress, preserves
the original brief on failure, supports safe retry/cancellation, and lets users
request a focused update after the first result. Detailed discovery, sources,
formal SRS controls, and documentation-package exports remain under **Advanced
project details**.
