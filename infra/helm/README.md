# Helm — how the FlexSim services are deployed

(folder: infra\helm)

From now on, the 5 Spring Boot services are installed and updated ONLY with Helm.
Do NOT use `kubectl apply` on them any more (the old plain YAML is in
`infra\k8s\old-plain-yaml\` for learning only).

## Folders

```
infra\helm\
├── flexsim-service\        the ONE chart for all services (templates with blanks)
│   ├── Chart.yaml           chart ID card (raise "version" when you change the chart)
│   ├── values.yaml          defaults for all services (probes, memory, security)
│   └── templates\           deployment.yaml, service.yaml, configmap.yaml, _helpers.tpl
├── values\
│   └── local\               one small file per service for the laptop (only the differences)
└── tools\
    └── reset-managed-fields.json   one-time tool used when we moved from kubectl to Helm
```

## Before any command

```
kubectl config current-context      # must be docker-desktop on the laptop
cd C:\Users\neela\Documents\flexsim-project
```

## Everyday commands (example: kpi-service)

| What | Command |
|---|---|
| Check the chart | `helm lint infra\helm\flexsim-service -f infra\helm\values\local\kpi-service.yaml` |
| See the final YAML (installs nothing) | `helm template kpi-service infra\helm\flexsim-service -n flexsim -f infra\helm\values\local\kpi-service.yaml` |
| Install or update | `helm upgrade --install kpi-service infra\helm\flexsim-service -n flexsim -f infra\helm\values\local\kpi-service.yaml` |
| List releases | `helm list -n flexsim` |
| History of one release | `helm history kpi-service -n flexsim` |
| Go back to revision N | `helm rollback kpi-service N -n flexsim` |

## New version of a service

1. Build the image with a NEW tag: `docker build -t kpi-service:0.1.1 .` (never reuse a tag)
2. Change `image.tag` in `values\local\kpi-service.yaml`
3. `helm upgrade --install ...` (command above)
4. `kubectl rollout status deployment/kpi-service -n flexsim`

## Change a setting (non-secret)

1. Edit `config:` in `values\local\<service>.yaml`
2. `helm upgrade --install ...` → the checksum changes → the pods restart by themselves

## Change something for ALL services (probes, memory, security)

1. Edit `flexsim-service\values.yaml` or a template
2. Raise `version` in `flexsim-service\Chart.yaml` (0.1.1 → 0.1.2)
3. Upgrade the 5 releases ONE AT A TIME (5 Java apps starting together is slow on a laptop)

## Secrets

Not in Helm and never in Git. Created once per service:
```
kubectl create secret generic kpi-service-secret -n flexsim --from-env-file=C:\Users\neela\Documents\flexsim-project\services\kpi-service\.env
```

## Never

- `helm uninstall <service>` unless you WANT the service deleted
- `kubectl apply` / `kubectl edit` on Helm-managed objects (Helm will overwrite it, or conflict)
- passwords in any values file
