# ABLSoft Product Inventory

Spring Boot API (`backend/`) + Angular app served by nginx (`frontend/`), wired together with a single `docker-compose.yml`.

## Running the application

```bash
docker compose up -d --build
```

- Frontend: http://localhost:4200
- Backend API: http://localhost:8080

The frontend's nginx proxies `/api/` to the backend container, so no extra network setup or configuration is needed — a single `docker compose up` builds and starts both services.

To stop:

```bash
docker compose down
```

Add `-v` to also delete the H2 database volume.
