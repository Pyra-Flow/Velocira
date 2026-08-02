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
