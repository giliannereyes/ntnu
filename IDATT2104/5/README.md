# C++ Code Runner (Vue + FastAPI + Docker)

This project lets a user write C++ code in the browser, compile it, run it on the server in Docker isolation, and show the result in the UI.

## Stack

- Frontend: Vue 3 + TypeScript + Tailwind + Vite
- Backend: Python + FastAPI
- Sandbox execution: Docker (`cpp-runner` image)

## Architecture

1. User writes C++ code in the frontend and clicks `Compile and Run`.
2. Frontend sends `POST /run` to FastAPI.
3. FastAPI writes code to a temporary file and launches a short-lived Docker container.
4. Container compiles with `g++` and runs executable with timeout.
5. FastAPI returns `stdout`, `stderr`, exit code, and timeout flag.
6. Frontend renders output.

## Security controls in this MVP

- Each execution runs in a new container (`docker run --rm`).
- No network inside execution container (`--network=none`).
- Resource limits: `--cpus=0.5`, `--memory=128m`, `--pids-limit=64`.
- Read-only container FS (`--read-only`) + dedicated tmpfs.
- Timeout on execution and API host side.
- Input size limit via Pydantic max length.

## Run locally

### 1) Build and start

```bash
docker compose up --build
```

### 2) Open app

- Frontend: http://localhost:5173
- Backend health: http://localhost:8000/health

## Troubleshooting

If you see `Docker CLI is not available on the server` or another Docker runtime error:

1. Rebuild services so new backend dependencies are installed:
```bash
docker compose down
docker compose up --build
```
2. Verify Docker Desktop / Docker Engine is running.
3. Verify backend has Docker socket mounted:
```bash
docker compose exec backend ls -l /var/run/docker.sock
```

## API contract

### Request

`POST /run`

```json
{
  "language": "cpp",
  "source_code": "#include <iostream>\nint main(){std::cout<<\"hi\";}",
  "timeout_seconds": 3
}
```

### Response

```json
{
  "stdout": "hi",
  "stderr": "",
  "exit_code": 0,
  "timed_out": false
}
```
