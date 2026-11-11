#!/usr/bin/env python3
"""Create isolated demo users and activity via the public development API."""
import json
import os
import time
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get("GATEWAY_URL", "http://localhost:8080").rstrip("/")


def request(method, path, token=None, body=None):
    data = None if body is None else json.dumps(body).encode()
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            content = response.read()
            return json.loads(content) if content else None
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"{method} {path}: HTTP {error.code}: {error.read().decode()}") from error


def register(label, suffix):
    return request("POST", "/auth/register", body={
        "email": f"analytics-{label}-{suffix}@example.test", "password": "Simulation2026!",
        "firstName": "Analytics", "lastName": label.capitalize()})["accessToken"]


def main():
    suffix = uuid.uuid4().hex[:12]
    owner = register("owner", suffix)
    worker = register("worker", suffix)
    request("POST", "/users/me/professional-profile", worker, {
        "biography": "Experienced plumbing professional for analytics simulation.",
        "categoryCodes": ["PLUMBING"], "cityCodes": ["CASABLANCA"]})
    details = {"title": "Analytics simulation", "description": "Repair a leaking pipe",
               "skill": "PLUMBING", "city": "CASABLANCA", "budget": 150,
               "estimatedDuration": "HALF_DAY"}
    job = request("POST", "/jobs", owner, details)["id"]
    application = request("POST", "/applications", worker, {"jobId": job})["id"]
    request("PATCH", f"/jobs/{job}/cancel", owner)
    deleted = request("POST", "/jobs", owner, details)["id"]
    request("DELETE", f"/jobs/{deleted}", owner)
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        jobs = request("GET", "/analytics/my/jobs", owner)
        apps = request("GET", "/analytics/my/applications", worker)
        if (jobs["total"] == 1 and jobs["records"][0]["job_id"] == job
                and jobs["records"][0]["status"] == "CANCELLED"
                and apps["total"] == 1 and apps["records"][0]["application_id"] == application
                and apps["records"][0]["status"] == "REJECTED"):
            print(f"Simulation complete: job {job}, application {application}, deleted job {deleted}")
            return
        time.sleep(1)
    raise RuntimeError("Analytics did not catch up within 60 seconds; inspect service and Kafka logs")


if __name__ == "__main__":
    main()
