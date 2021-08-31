# helm practice

## Deploy nginx With Helm

In this Chapter, we will dig deeper with Helm and demonstrate how to install the nginx web server via the following steps:

### Update the Chart Repository

```bash
# first, add the default repository, then update
helm repo add stable https://charts.helm.sh/stable
helm repo update

```

### Search Chart Repositories


Now that our repository Chart list has been updated, we can [search for
Charts](https://helm.sh/docs/helm/helm_search/).

To list all Charts:

```sh
helm search repo
```

That should output something similar to:
{{< output >}}
NAME                                    CHART VERSION   APP VERSION                     DESCRIPTION
stable/acs-engine-autoscaler            2.2.2           2.1.1                           Scales worker...
stable/aerospike                        0.3.2           v4.5.0.5                        A Helm chart...
...
{{< /output >}}

You can see from the output that it dumped the list of all Charts we have added.
In some cases that may be useful, but an even more useful search would involve a
keyword argument.  So next, we'll search just for `nginx`:

```sh
helm search repo nginx
```

That results in:
{{< output >}}
NAME                            CHART VERSION   APP VERSION     DESCRIPTION
stable/nginx-ingress            1.30.3          0.28.0          An nginx Ingress ...
stable/nginx-ldapauth-proxy     0.1.3           1.13.5          nginx proxy ...
stable/nginx-lego               0.3.1                           Chart for...
stable/gcloud-endpoints         0.1.2           1               DEPRECATED Develop...
...
{{< /output >}}

This new list of Charts are specific to nginx, because we passed the **nginx**
argument to the `helm search repo` command.

Further information on the command can be found [here](https://helm.sh/docs/helm/helm_search_repo/).

### Add the Bitnami Repository


In the last slide, we saw that nginx offers many different products via the
default Helm Chart repository, but the nginx standalone web server is not one of
them.

After a quick web search, we discover that there is a Chart for the nginx
standalone web server available via the [Bitnami Chart
repository](https://github.com/bitnami/charts).

To add the Bitnami Chart repo to our local list of searchable charts:

```sh
helm repo add bitnami https://charts.bitnami.com/bitnami
```

Once that completes, we can search all Bitnami Charts:

```sh
helm search repo bitnami
```

Which results in:

{{< output >}}
NAME                                    CHART VERSION   APP VERSION             DESCRIPTION
bitnami/bitnami-common                  0.0.8           0.0.8                   Chart with...
bitnami/apache                          4.3.3           1.10.9                  Chart for Apache...
bitnami/cassandra                       5.0.2           3.11.6                  Apache Cassandra...
...
{{< /output >}}

Search once again for nginx

```sh
helm search repo nginx
```

Now we are seeing more nginx options, across both repositories:

{{< output >}}
NAME                                    CHART VERSION   APP VERSION     DESCRIPTION
bitnami/nginx                           5.1.6           1.16.1          Chart for the nginx server
bitnami/nginx-ingress-controller        5.3.4           0.29.0          Chart for the nginx Ingress...
stable/nginx-ingress                    1.30.3          0.28.0          An nginx Ingress controller ...
{{< /output >}}

Or even search the Bitnami repo, just for nginx:

```sh
helm search repo bitnami/nginx
```

Which narrows it down to nginx on Bitnami:

{{< output >}}
NAME                                    CHART VERSION   APP VERSION     DESCRIPTION
bitnami/nginx                           5.1.6           1.16.1          Chart for the nginx server
bitnami/nginx-ingress-controller        5.3.4           0.29.0          Chart for the nginx Ingress...
{{< /output >}}

In both of those last two searches, we see

{{< output >}}
bitnami/nginx
{{< /output >}}

as a search result.  That's the one we're looking for, so let's use Helm to install it to the EKS cluster.

### Install bitnami/nginx

Installing the Bitnami standalone nginx web server Chart involves us using the
[helm install](https://helm.sh/docs/helm/helm_install/) command.

A Helm Chart can be installed multiple times inside a Kubernetes cluster. This
is because each installation of a Chart can be customized to suit a different
purpose.

For this reason, you must supply a unique name for the installation, or ask Helm
to generate a name for you.

#### Challenge:
**How can you use Helm to deploy the bitnami/nginx chart?**

**HINT:** Use the `helm` utility to `install` the `bitnami/nginx` chart
and specify the name `mywebserver` for the Kubernetes deployment. Consult the
[helm install](https://helm.sh/docs/intro/quickstart/#install-an-example-chart)
documentation or run the `helm install --help` command to figure out the
syntax.

{{%expand "Expand here to see the solution" %}}
```sh
helm install mywebserver bitnami/nginx
```
{{% /expand %}}

Once you run this command, the output will contain the information about the deployment status, revision, namespace, etc, similar to:

{{< output >}}
NAME: mywebserver
LAST DEPLOYED: Tue Feb 18 22:02:13 2020
NAMESPACE: default
STATUS: deployed
REVISION: 1
TEST SUITE: None
NOTES:
Get the NGINX URL:

  NOTE: It may take a few minutes for the LoadBalancer IP to be available.
        Watch the status with: 'kubectl get svc --namespace default -w mywebserver-nginx'

  export SERVICE_IP=$(kubectl get svc --namespace default mywebserver-nginx --template "{{ range (index .status.loadBalancer.ingress 0) }}{{.}}{{ end }}")
  echo "NGINX URL: http://$SERVICE_IP/"
{{< /output >}}

In order to review the underlying Kubernetes services, pods and deployments, run:
```sh
kubectl get svc,po,deploy
```

{{% notice info %}}
In the following `kubectl` command examples, it may take a minute or two for
each of these objects' `DESIRED` and `CURRENT` values to match; if they don't
match on the first try, wait a few seconds, and run the command again to check
the status.
{{% /notice %}}

The first object shown in this output is a
[Deployment](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/).
A Deployment object manages rollouts (and rollbacks) of different versions of an
application.

You can inspect this Deployment object in more detail by running the following
command:

```
kubectl describe deployment mywebserver
```

The next object shown created by the Chart is a
[Pod](https://kubernetes.io/docs/concepts/workloads/pods/pod/).  A Pod is a
group of one or more containers.

To verify the Pod object was successfully deployed, we can run the following
command:

```
kubectl get pods -l app.kubernetes.io/name=nginx
```
And you should see output similar to:

{{< output >}}
NAME                                 READY     STATUS    RESTARTS   AGE
mywebserver-nginx-85985c8466-tczst   1/1       Running   0          10s
{{< /output >}}

The third object that this Chart creates for us is a
[Service](https://kubernetes.io/docs/concepts/services-networking/service/). A
Service enables us to contact this nginx web server from the Internet, via an
Elastic Load Balancer (ELB).

To get the complete URL of this Service, run:

```
kubectl get service mywebserver-nginx -o wide
```

That should output something similar to:

{{< output >}}
NAME                TYPE           CLUSTER-IP      EXTERNAL-IP
mywebserver-nginx   LoadBalancer   10.100.223.99   abc123.amazonaws.com
{{< /output >}}

Copy the value for `EXTERNAL-IP`, open a new tab in your web browser, and
paste it in.

{{% notice info %}}
It may take a couple minutes for the ELB and its associated DNS name to become
available; if you get an error, wait one minute, and hit reload.
{{% /notice %}}

When the Service does come online, you should see a welcome message similar to:

![Helm Logo](/images/helm-nginx/welcome_to_nginx.png)

Congratulations!  You've now successfully deployed the nginx standalone web
server to your EKS cluster!

### Clean Up
To remove all the objects that the Helm Chart created, we can use [Helm
uninstall](https://helm.sh/docs/helm/helm_uninstall/).

Before we uninstall our application, we can verify what we have running via the
[Helm list](https://helm.sh/docs/helm/helm_list/) command:

```sh
helm list
```

You should see output similar to below, which show that mywebserver is installed:
{{< output >}}
NAME            NAMESPACE       REVISION        UPDATED                                 STATUS          CHART           APP VERSION
mywebserver     default         1               2020-02-18 22:02:13.844416354 +0100 CET deployed        nginx-5.1.6     1.16.1
{{< /output >}}

It was a lot of fun; we had some great times sending HTTP back and forth, but now its time to uninstall this deployment.  To uninstall:

```sh
helm uninstall mywebserver
```

And you should be met with the output:
{{< output >}}
release "mywebserver" uninstalled
{{< /output >}}

kubectl will also demonstrate that our pods and service are no longer available:

```sh
kubectl get pods -l app.kubernetes.io/name=nginx
kubectl get service mywebserver-nginx -o wide
```

As would trying to access the service via the web browser via a page reload.

With that, cleanup is complete.

## Deploy Example Microservices Using Helm

### CREATE A CHART

Helm charts have a structure similar to:

{{< output >}}
/eksdemo
  /Chart.yaml  # a description of the chart
  /values.yaml # defaults, may be overridden during install or upgrade
  /charts/ # May contain subcharts
  /templates/ # the template files themselves
  ...
{{< /output >}}

We'll follow this template, and create a new chart called **eksdemo** with the following commands:

```sh
cd ~/environment
helm create eksdemo
```

### CUSTOMIZE DEFAULTS

If you look in the newly created **eksdemo** directory, you'll see several files and directories. Specifically, inside the /templates directory, you'll see:

* `deployment.yaml`: A basic manifest for creating a Kubernetes deployment
* `_helpers.tpl`: A place to put template helpers that you can re-use throughout the chart
* `ingress.yaml`: A basic manifest for creating a Kubernetes ingress object for your service
* `NOTES.txt`: The "help text" for your chart. This will be displayed to your users when they run helm install.
* `serviceaccount.yaml`: A basic manifest for creating service account.
* `service.yaml`: A basic manifest for creating a service endpoint for your deployment
* `tests/`: A folder which contains tests for chart

We're actually going to create our own files, so we'll delete these boilerplate files

```sh
rm -rf ~/environment/eksdemo/templates/
rm ~/environment/eksdemo/Chart.yaml
rm ~/environment/eksdemo/values.yaml
```

Run the following code block to create a new Chart.yaml file which will describe the chart

```sh
cat <<EoF > ~/environment/eksdemo/Chart.yaml
apiVersion: v2
name: eksdemo
description: A Helm chart for EKS Workshop Microservices application
version: 0.1.0
appVersion: 1.0
EoF
```

Next we'll copy the manifest files for each of our microservices into the templates directory as *servicename*.yaml

```sh
#create subfolders for each template type
mkdir -p ~/environment/eksdemo/templates/deployment
mkdir -p ~/environment/eksdemo/templates/service

# Copy and rename frontend manifests
cp ~/environment/ecsdemo-frontend/kubernetes/deployment.yaml ~/environment/eksdemo/templates/deployment/frontend.yaml
cp ~/environment/ecsdemo-frontend/kubernetes/service.yaml ~/environment/eksdemo/templates/service/frontend.yaml

# Copy and rename crystal manifests
cp ~/environment/ecsdemo-crystal/kubernetes/deployment.yaml ~/environment/eksdemo/templates/deployment/crystal.yaml
cp ~/environment/ecsdemo-crystal/kubernetes/service.yaml ~/environment/eksdemo/templates/service/crystal.yaml

# Copy and rename nodejs manifests
cp ~/environment/ecsdemo-nodejs/kubernetes/deployment.yaml ~/environment/eksdemo/templates/deployment/nodejs.yaml
cp ~/environment/ecsdemo-nodejs/kubernetes/service.yaml ~/environment/eksdemo/templates/service/nodejs.yaml
```

All files in the templates directory are sent through the template engine. These are currently plain YAML files that would be sent to Kubernetes as-is.

## Replace hard-coded values with template directives
Let's replace some of the values with `template directives` to enable more customization by removing hard-coded values.

Open ~/environment/eksdemo/templates/deployment/frontend.yaml in your Cloud9 editor.

{{% notice info %}}
The following steps should be completed seperately for **frontend.yaml**, **crystal.yaml**, and **nodejs.yaml**.
{{% /notice %}}

Under `spec`, find **replicas: 1**  and replace with the following:

```yaml
replicas: {{ .Values.replicas }}
```

Under `spec.template.spec.containers.image`, replace the image with the correct template value from the table below:

|Filename | Value |
|---|---|
|frontend.yaml|- image: {{ .Values.frontend.image }}:{{ .Values.version }}|
|crystal.yaml|- image: {{ .Values.crystal.image }}:{{ .Values.version }}|
|nodejs.yaml|- image: {{ .Values.nodejs.image }}:{{ .Values.version }}|

#### Create a values.yaml file with our template defaults

Run the following code block to populate our `template directives` with default values.

```sh
cat <<EoF > ~/environment/eksdemo/values.yaml
# Default values for eksdemo.
# This is a YAML-formatted file.
# Declare variables to be passed into your templates.

# Release-wide Values
replicas: 3
version: 'latest'

# Service Specific Values
nodejs:
  image: brentley/ecsdemo-nodejs
crystal:
  image: brentley/ecsdemo-crystal
frontend:
  image: brentley/ecsdemo-frontend
EoF
```

### DEPLOY THE EKSDEMO CHART

#### Use the dry-run flag to test our templates

To test the syntax and validity of the Chart without actually deploying it,
we'll use the `--dry-run` flag.

The following command will build and output the rendered templates without
installing the Chart:

```sh
helm install --debug --dry-run workshop ~/environment/eksdemo
```

Confirm that the values created by the template look correct.

#### DEPLOY THE EKSDEMO CHART

Now that we have tested our template, let's install it.

```sh
helm install workshop ~/environment/eksdemo
```

Similar to what we saw previously in the [nginx Helm Chart
example](/beginner/060_helm/helm_nginx/index.html), an output of the command will contain the information about the deployment status, revision, namespace, etc, similar to:

{{< output >}}
NAME: workshop
LAST DEPLOYED: Tue Feb 18 22:11:37 2020
NAMESPACE: default
STATUS: deployed
REVISION: 1
TEST SUITE: None
{{< /output >}}

In order to review the underlying services, pods and deployments, run:

```sh
kubectl get svc,po,deploy
```

#### TEST THE SERVICE

To test the service our eksdemo Chart created, we'll need to get the name of the ELB endpoint that was generated when we deployed the Chart:

```sh
kubectl get svc ecsdemo-frontend -o jsonpath="{.status.loadBalancer.ingress[*].hostname}"; echo
```

Copy that address, and paste it into a new tab in your browser.  You should see something similar to:

![Example Service](https://www.eksworkshop.com/images/helm_micro/micro_example.png)

#### ROLLING BACK

Mistakes will happen during deployment, and when they do, Helm makes it easy to undo, or "roll back" to the previously deployed version.

#### Update the demo application chart with a breaking change

Open **values.yaml** and modify the image name under `nodejs.image` to **brentley/ecsdemo-nodejs-non-existing**. This image does not exist, so this will break our deployment.

Deploy the updated demo application chart:

```sh
helm upgrade workshop ~/environment/eksdemo
```

The rolling upgrade will begin by creating a new nodejs pod with the new image. The new `ecsdemo-nodejs` Pod should fail to pull non-existing image. Run `kubectl get pods` to see the `ImagePullBackOff` error.

```sh
kubectl get pods
```

{{< output >}}
NAME                               READY   STATUS             RESTARTS   AGE
ecsdemo-crystal-844d84cb86-56gpz   1/1     Running            0          23m
ecsdemo-crystal-844d84cb86-5vvcg   1/1     Running            0          23m
ecsdemo-crystal-844d84cb86-d2plf   1/1     Running            0          23m
ecsdemo-frontend-6df6d9bb9-dpcsl   1/1     Running            0          23m
ecsdemo-frontend-6df6d9bb9-lzlwh   1/1     Running            0          23m
ecsdemo-frontend-6df6d9bb9-psg69   1/1     Running            0          23m
ecsdemo-nodejs-6fdf964f5f-6cnzl    1/1     Running            0          23m
ecsdemo-nodejs-6fdf964f5f-fbcjv    1/1     Running            0          23m
ecsdemo-nodejs-6fdf964f5f-v88jn    1/1     Running            0          23m
ecsdemo-nodejs-7c6575b56c-hrrsp    0/1     ImagePullBackOff   0          15m
{{< /output >}}

Run `helm status workshop` to verify the `LAST DEPLOYED` timestamp.

```sh
helm status workshop
```

{{< output >}}
LAST DEPLOYED: Tue Feb 18 22:14:00 2020
NAMESPACE: default
STATUS: deployed
...
{{< /output >}}

This should correspond to the last entry on `helm history workshop`

```sh
helm history workshop
```

#### Rollback the failed upgrade

Now we are going to rollback the application to the previous working release revision.

First, list Helm release revisions:

```sh
helm history workshop
```

Then, rollback to the previous application revision (can rollback to any revision too):

```sh
# rollback to the 1st revision
helm rollback workshop 1
```

Validate `workshop` release status with:

```sh
helm status workshop
```

Verify that the error is gone

```sh
kubectl get pods
```

{{< output >}}
NAME                               READY   STATUS             RESTARTS   AGE
ecsdemo-crystal-844d84cb86-56gpz   1/1     Running            0          23m
ecsdemo-crystal-844d84cb86-5vvcg   1/1     Running            0          23m
ecsdemo-crystal-844d84cb86-d2plf   1/1     Running            0          23m
ecsdemo-frontend-6df6d9bb9-dpcsl   1/1     Running            0          23m
ecsdemo-frontend-6df6d9bb9-lzlwh   1/1     Running            0          23m
ecsdemo-frontend-6df6d9bb9-psg69   1/1     Running            0          23m
ecsdemo-nodejs-6fdf964f5f-6cnzl    1/1     Running            0          23m
ecsdemo-nodejs-6fdf964f5f-fbcjv    1/1     Running            0          23m
ecsdemo-nodejs-6fdf964f5f-v88jn    1/1     Running            0          23m
{{< /output >}}

### CLEANUP

To delete the workshop release, run:

```sh
helm uninstall workshop
```

## Install Jenkins using helm

* use following link to install the approprite helm chart [here](https://artifacthub.io/packages/helm/jenkinsci/jenkins)


