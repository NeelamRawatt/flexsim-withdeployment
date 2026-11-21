# Deploying our services to LOCAL Kubernetes (Docker Desktop)

Folder: `infra\k8s\` (one folder per service, 3 files each: configmap, deployment, service).
Run `kubectl` commands from the project root unless a step says otherwise.

## One-time setup (done once per laptop)

| Step | Command | Why |
|---|---|---|
| A | Docker Desktop -> Settings -> Kubernetes -> Enable Kubernetes (kubeadm) | a small Kubernetes on the laptop |
| B | `kubectl config current-context`  -> must say `docker-desktop` | make sure we talk to the LAPTOP cluster |
| C | `kubectl create namespace flexsim` | our own "department" in the cluster |
| D | `kubectl config set-context --current --namespace=flexsim` | all commands go to `flexsim` by default |
| E | Docker Compose must run: postgres + kafka (`docker ps`) | the database and Kafka stay OUTSIDE Kubernetes (like Azure later) |
| F | After changing docker-compose.kafka.yml (new door 9094 for pods): `docker compose -f docker-compose.yml -f docker-compose.kafka.yml up -d kafka` (in `infra\docker`) | Kafka gets the extra door for pods |

## Deploying ONE service (the same 6 steps for every service)

Replace `<service>` with: auth-service, experiment-service, simulation-service, kpi-service, file-service.

| Step | Command | Why | Good result |
|---|---|---|---|
| 1. Build the image | `cd services\<service>` then `docker build -t <service>:0.1.0 .` | Kubernetes runs IMAGES | `naming to docker.io/library/<service>:0.1.0` |
| 2. Create the Secret (passwords) | `kubectl create secret generic <service>-secret --from-env-file=C:\Users\neela\Documents\flexsim-project\services\<service>\.env` | passwords never go into a file in Git | `secret/<service>-secret created` |
| 3. Apply the 3 files | `kubectl apply -f C:\Users\neela\Documents\flexsim-project\infra\k8s\<service>` | creates the ConfigMap, Deployment and Service | 3 lines `... created` |
| 4. Watch it start | `kubectl get pods -w` (Ctrl + C to stop) | Java needs 20-60 s | `<service>-...  1/1  Running` |
| 5. Check the logs | `kubectl logs deployment/<service>` | see Spring Boot start | `Started ...Application in ... seconds` |
| 6. Test it by name | `kubectl run test-curl --rm -it --image=curlimages/curl:8.11.1 --restart=Never -- curl -s http://<service>:<port>/actuator/health` | how services call each other | `{"status":"UP",...}` |

Ports: auth 8081, experiment 8082, simulation 8083, kpi 8084, file 8086.

Deploy order (because of what each one needs): auth -> experiment -> file -> simulation -> kpi.

## Changes later

| Want | Do | Command |
|---|---|---|
| More pods | change `replicas:` in deployment.yaml | `kubectl apply -f infra\k8s\<service>` |
| New version | `docker build -t <service>:0.1.1 .`, change `image:` in deployment.yaml | `kubectl apply -f ...` then `kubectl rollout status deployment/<service>` |
| Go back | (emergency) | `kubectl rollout undo deployment/<service>` and fix the file in Git too |
| Changed .env | delete + create the Secret again, then restart | `kubectl delete secret <service>-secret`, step 2, `kubectl rollout restart deployment/<service>` |
| Remove a service | | `kubectl delete -f infra\k8s\<service>` |

## When something fails

| You see | Run | Usual cause |
|---|---|---|
| `CreateContainerConfigError` | `kubectl describe pod <pod>` (Events) | Secret/ConfigMap name does not match |
| `ErrImageNeverPull` / `ImagePullBackOff` | `kubectl describe pod <pod>` | image name/tag differs from what you built |
| `CrashLoopBackOff` | `kubectl logs <pod> --previous` | the program fails at start (database / Kafka / settings) |
| stays `0/1 Running` | `kubectl logs <pod>` | still starting, or readiness says "not ready" |
| `OOMKilled` (describe) | `kubectl describe pod <pod>` | needs more memory: raise `limits.memory` |

Not in Kubernetes: **flexsim-worker** runs on a Windows VM next to FlexSim (see services\flexsim-worker\deploy\windows).
