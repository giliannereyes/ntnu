import base64
import os
import shutil
import subprocess
from typing import Literal

import docker
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

MAX_CODE_SIZE = 100_000
DEFAULT_TIMEOUT_SECONDS = 3
MAX_TIMEOUT_SECONDS = 8
CPP_IMAGE = os.getenv("CPP_IMAGE", "cpp-runner:latest")
DOCKER_BIN = os.getenv("DOCKER_BIN", "docker")

class RunRequest(BaseModel):
    language: Literal["cpp"] = "cpp"
    source_code: str = Field(..., min_length=1, max_length=MAX_CODE_SIZE)
    timeout_seconds: int = Field(DEFAULT_TIMEOUT_SECONDS, ge=1, le=MAX_TIMEOUT_SECONDS)


class RunResponse(BaseModel):
    stdout: str
    stderr: str
    exit_code: int
    timed_out: bool


app = FastAPI(title="Code Runner API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


def _run_cpp_in_docker(source_code: str, timeout_seconds: int) -> RunResponse:
    encoded_source = base64.b64encode(source_code.encode("utf-8")).decode("ascii")

    compile_and_run_script = (
        "echo \"$SOURCE_B64\" | base64 -d > /workspace/main.cpp "
        "&& g++ -std=c++17 -O2 -pipe -o /workspace/main /workspace/main.cpp "
        "&& timeout {t}s /workspace/main"
    ).format(t=timeout_seconds)

    """
    docker_bin = shutil.which(DOCKER_BIN)
    if docker_bin:
        return _run_cpp_with_cli(docker_bin, encoded_source, compile_and_run_script, timeout_seconds)
    """
    return _run_cpp_with_sdk(encoded_source, compile_and_run_script, timeout_seconds)

"""
def _run_cpp_with_cli(
    docker_bin: str,
    encoded_source: str,
    compile_and_run_script: str,
    timeout_seconds: int,
) -> RunResponse:
    cmd = [
        docker_bin,
        "run",
        "--rm",
        "--network=none",
        "--cpus=0.5",
        "--memory=128m",
        "--pids-limit=64",
        "--read-only",
        "--tmpfs",
        "/tmp:rw,noexec,nosuid,size=16m",
        "--tmpfs",
        "/workspace:rw,exec,nosuid,size=64m",
        "--workdir",
        "/workspace",
        "-e",
        f"SOURCE_B64={encoded_source}",
        CPP_IMAGE,
        "sh",
        "-lc",
        compile_and_run_script,
    ]

    try:
        completed = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=timeout_seconds + 2,
            check=False,
        )
    except subprocess.TimeoutExpired as exc:
        stdout = exc.stdout or ""
        stderr = (exc.stderr or "") + "\nExecution timed out on host side."
        return RunResponse(stdout=stdout, stderr=stderr.strip(), exit_code=124, timed_out=True)

    timed_out = completed.returncode == 124
    return RunResponse(
        stdout=completed.stdout,
        stderr=completed.stderr,
        exit_code=completed.returncode,
        timed_out=timed_out,
    )
"""

def _run_cpp_with_sdk(
    encoded_source: str,
    compile_and_run_script: str,
    timeout_seconds: int,
) -> RunResponse:
    container = None
    try:
        client = docker.from_env()
        container = client.containers.run(
            CPP_IMAGE,
            command=["sh", "-lc", compile_and_run_script],
            detach=True,
            network_disabled=True,
            mem_limit="128m",
            nano_cpus=500_000_000,
            pids_limit=64,
            read_only=True,
            tmpfs={
                "/tmp": "rw,noexec,nosuid,size=16m",
                "/workspace": "rw,exec,nosuid,size=64m",
            },
            working_dir="/workspace",
            environment={"SOURCE_B64": encoded_source},
        )
    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail=f"Docker runtime is unavailable: {exc}",
        )
    try:
        result = container.wait(timeout=timeout_seconds + 2)
    except Exception:
        try:
            container.kill()
        except Exception:
            pass
        return RunResponse(stdout="", stderr="Execution timed out on host side.", exit_code=124, timed_out=True)
    try:
        stdout = container.logs(stdout=True, stderr=False).decode("utf-8", errors="replace")
        stderr = container.logs(stdout=False, stderr=True).decode("utf-8", errors="replace")
        exit_code = int(result.get("StatusCode", 1))
        timed_out = exit_code == 124
        return RunResponse(
            stdout=stdout,
            stderr=stderr,
            exit_code=exit_code,
            timed_out=timed_out,
        )
    finally:
        try:
            container.remove(force=True)
        except Exception:
            pass

@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/run", response_model=RunResponse)
def run_code(payload: RunRequest) -> RunResponse:
    if payload.language != "cpp":
        raise HTTPException(status_code=400, detail="Only 'cpp' is supported.")

    return _run_cpp_in_docker(payload.source_code, payload.timeout_seconds)
