# FlexSim Platform

A logistics simulation platform, split into small services (microservices).

## Folders

| Folder | What it holds |
|---|---|
| `services/` | One folder per service (login, experiments, simulation, results, files, gateway, chatbot, FlexSim worker) |
| `infra/` | Everything for running the system: database recipe, Kubernetes, Azure |
| `docs/` | Notes and decisions |

## Rules of this project

1. One service = one database = one database user.
2. No password is ever saved in Git. Each part has its own `.env` file (ignored by Git) and a `.env.example` template (saved in Git).
3. Every change is a Git commit with a clear message.