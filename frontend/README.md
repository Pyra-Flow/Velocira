# Velocira frontend

This Next.js application is the browser client for the Velocira API. It is
normally started with the rest of the stack from the repository root:

```powershell
docker compose up --build --wait
```

See the [repository README](../README.md) for the required `.env` setup,
published ports, and commands for starting or stopping the complete system.

## Run the frontend by itself

Install the locked dependencies, then point the browser client at a running
backend. The default API URL is `http://localhost:8080/api`.

```powershell
npm ci
$env:NEXT_PUBLIC_API_URL = "http://localhost:8080/api" # optional when using the default
npm run dev
```

Open <http://localhost:3000>. Authentication, projects, generation, and
evidence workflows require the backend and AI service to be running.

## Validate changes

```powershell
npm run typecheck
npm run lint
npm run build
```

`NEXT_PUBLIC_API_URL` and `NEXT_PUBLIC_GOOGLE_CLIENT_ID` are build-time public
configuration values. Do not place server secrets, provider keys, or database
credentials in frontend environment variables.
