{{- /*
=====================================================================
_helpers.tpl   (folder: infra\helm\flexsim-service\templates)

Small reusable pieces ("named templates"), written ONCE and used in
deployment.yaml, service.yaml and configmap.yaml.

Why the name starts with "_": Helm does NOT turn files starting with "_"
into Kubernetes objects. This file only holds helpers.

How to read the symbols:
  {{ ... }}          = a blank that Helm fills in
  {{- ... -}}        = the "-" removes empty spaces/new lines on that side,
                       so the final YAML has no empty gaps
  define "x"         = make a helper called x
  include "x" .      = use helper x here (the "." passes all values to it)
  "slash-star ... star-slash" = a comment inside the curly braces; it never
                       appears in the output (written in words here, because
                       the real symbols would end THIS comment too early)
=====================================================================
*/ -}}

{{- /*
flexsim-service.name
The service name, e.g. "auth-service".
"required" STOPS the install with a clear message if a values file forgot it.
This is better than installing something broken with an empty name.
*/ -}}
{{- define "flexsim-service.name" -}}
{{- required "values: 'name' is required (e.g. auth-service)" .Values.name -}}
{{- end -}}

{{- /*
flexsim-service.selectorLabels
The labels that CONNECT things:
  Deployment -> its pods, and Service -> the pods.
These must NEVER change after the first install (Kubernetes does not allow
changing a Deployment's selector). So we keep them small: only "app".
"app: auth-service" is the same label your old plain YAML used.
*/ -}}
{{- define "flexsim-service.selectorLabels" -}}
app: {{ include "flexsim-service.name" . }}
{{- end -}}

{{- /*
flexsim-service.labels
All labels for every object. The selector label above + standard labels
that tools (Azure portal, monitoring, kubectl filters) understand:
  app.kubernetes.io/name       = what it is
  app.kubernetes.io/instance   = the Helm release name
  app.kubernetes.io/version    = the image tag that runs
  app.kubernetes.io/managed-by = "Helm" (so people know: do not edit by hand)
  helm.sh/chart                = chart name + chart version that made it
"quote" puts the value in "..." so YAML reads it as text (0.1.0 stays text).
*/ -}}
{{- define "flexsim-service.labels" -}}
{{ include "flexsim-service.selectorLabels" . }}
app.kubernetes.io/name: {{ include "flexsim-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Values.image.tag | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end -}}

{{- /*
flexsim-service.image
The full image name: repository:tag
  repository empty -> use the name   (laptop: auth-service:0.1.0)
  repository set   -> use it         (Azure:  xyz.azurecr.io/auth-service:0.1.0)
"default" = use the second value when the first one is empty.
*/ -}}
{{- define "flexsim-service.image" -}}
{{- $repo := default (include "flexsim-service.name" .) .Values.image.repository -}}
{{- $tag := required "values: 'image.tag' is required (e.g. 0.1.0)" .Values.image.tag -}}
{{- printf "%s:%s" $repo $tag -}}
{{- end -}}

{{- /*
flexsim-service.secretName
Name of the Secret with the passwords. Empty -> "<name>-secret".
*/ -}}
{{- define "flexsim-service.secretName" -}}
{{- default (printf "%s-secret" (include "flexsim-service.name" .)) .Values.secretName -}}
{{- end -}}

{{- /*
flexsim-service.port
The port. "fail" stops the install with a message if it is missing or 0.
("required" alone would accept 0, so we check it ourselves with "if not".)
*/ -}}
{{- define "flexsim-service.port" -}}
{{- if not .Values.port -}}
{{- fail "values: 'port' is required (e.g. 8081)" -}}
{{- end -}}
{{- .Values.port -}}
{{- end -}}
