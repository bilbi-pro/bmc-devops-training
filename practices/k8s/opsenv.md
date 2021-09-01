# ops env

## jenkins

```bash
helm repo add jenkins https://charts.jenkins.io
helm repo update
helm show values jenkins/jenkins

helm install jenkins -n jenkins jenkins/jenkins [flags]

```

## Prometheus

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add kube-state-metrics https://kubernetes.github.io/kube-state-metrics
helm repo update
helm show values prometheus-community/prometheus

helm install [RELEASE_NAME] prometheus-community/prometheus

```

## Fluentd

```bash
helm repo add bitnami https://charts.bitnami.com/bitnamihelm repo update
helm show values prometheus-community/prometheus

helm install [RELEASE_NAME] bitnami/fluentd

```

## ELK

```bash
helm repo add elastic https://helm.elastic.co
helm install elasticsearch elastic/elasticsearch
helm install kibana elastic/kibana

```

## install helm from local files

```bash
helm template -f ./chart-values/chart-values.yaml release-name ./charts/chart/ > ./outputs/chart-release-deployment.yaml
helm install -f ./chart-values/chart-values.yaml release-name ./charts/chart/
helm upgrade -f ./chart-values/chart-values.yaml release-name ./charts/chart/

```
