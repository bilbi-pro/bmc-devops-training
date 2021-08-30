# minikube setup

* open new instance on AWS, following is the user data to use (select ubuntu AMI)


```bash

#!/bin/bash -f

sudo apt-get update

#Install packages to allow apt to use a repository over HTTPS:
sudo apt-get install -y apt-transport-https \
  ca-certificates \
  curl \
  software-properties-common \
  jq \
  conntrack 

#Add Docker’s official GPG key:

curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -

sudo add-apt-repository \
  "deb [arch=amd64] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) \
  stable"

sudo apt-get update && sudo apt-get install -y docker-ce

#install compose
sudo curl -L https://github.com/docker/compose/releases/download/1.19.0/docker-compose-`uname -s`-`uname -m` -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose


#install kubectl
curl -LO https://storage.googleapis.com/kubernetes-release/release/v1.18.0/bin/linux/amd64/kubectl

#Make the kubectl binary executable.
chmod +x ./kubectl

#Move the binary in to your PATH.
sudo mv ./kubectl /usr/bin/kubectl

echo "source <(kubectl completion bash)" >> ~/.bashrc

sudo usermod -aG docker $USER 

curl -Lo minikube https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64 \
  && chmod +x ./minikube

sudo ./minikube start --vm-driver=none --kubernetes-version=v1.18.0 --extra-config=kubeadm.ignore-preflight-errors=SystemVerification --extra-config=kubelet.resolv-conf=/run/systemd/resolve/resolv.conf --extra-config=kubeadm.ignore-preflight-errors=NumCPU
# give user permissions to kubectl config
sudo chown -R $USER $HOME/.kube $HOME/.minikube

#install kube-ps1
cd ~/
git clone https://github.com/jonmosco/kube-ps1.git
echo 'source ~/kube-ps1/kube-ps1.sh' >> ~/.bashrc
echo "PS1='[\u@\h \W \$(kube_ps1)]\$ '" >> ~/.bashrc
cd -

#install kubens and kubectx
git clone https://github.com/ahmetb/kubectx.git ~/.kubectx
COMPDIR=/usr/share/bash-completion/completions
sudo ln -sf ~/.kubectx/completion/kubens.bash $COMPDIR/kubens
sudo ln -sf ~/.kubectx/completion/kubectx.bash $COMPDIR/kubectx
cat << FOE >> ~/.bashrc


#kubectx and kubens
export PATH=~/.kubectx:\$PATH
FOE
```

## amzon linux user data
```
#!/bin/sh



install_req(){
    apt update -y
    apt upgrade -y
    apt-get update -y 
    PACKAGES="openjdk-11-jre-headless net-tools nano wget unzip bash-completion git git-lfs awscli ansible apt-transport-https ca-certificates curl gnupg-agent software-properties-common maven allure gradle xvfb groovy ansible scala python-all-dev htop nodejs npm"
    apt install -y $PACKAGES
    apt-get install -qqy apt-utils
    apt-get -qqy update
    echo "after update"
    PREREQ_PACKAGE="sudo ssh wget curl supervisor jq \
    python2.7 git libpq-dev python-dev libffi-dev \
    nginx libcairo2 libpango1.0-0 g++ libyaml-cpp-dev \
    htop gettext libgettextpo-dev libsm6 nginx-extras \
    libxml2-dev libxslt1-dev libkrb5-dev build-essential \
    libssl-dev libxmlsec1-dev pkg-config"
    apt-get -qqy install ${PREREQ_PACKAGE}
    curl https://bootstrap.pypa.io/get-pip.py --output get-pip.py
    python2 get-pip.py
}
install_amazon_stack(){
    snap install -y amazon-ssm-agent
    curl -O https://inspector-agent.amazonaws.com/linux/latest/install
    chmod +x ./install
    ./install
    rm ./install
}

install_Docker(){
    apt-key fingerprint 0EBFCD88
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
    add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu focal stable"
    apt-get update -y 
    local PACKAGES=" curl gnupg-agent software-properties-common docker-ce docker-ce-cli containerd.io"
    apt install -y $PACKAGES
    groupadd docker
    usermod -aG docker $USER
    usermod -aG docker ubuntu
    curl -LO "https://storage.googleapis.com/kubernetes-release/release/$(curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt)/bin/linux/amd64/kubectl"
    chmod +x ./kubectl
    mv ./kubectl /usr/local/bin/kubectl
}

install_opstools(){
    #rabbitmq, pgSQL client, 
    wget -q -O - https://www.apache.org/dist/cassandra/KEYS | sudo apt-key add -
    sudo sh -c 'echo "deb http://www.apache.org/dist/cassandra/debian 311x main" > /etc/apt/sources.list.d/cassandra.list'
    
    sudo apt update
    apt install -y amqp-tools rabbitmq-server cassandra-tools redis-server
}

install_buildTools(){
    #dependency-check location: /usr/share/dependency-check/
    pip install dependency-check 
}

install_devTools(){
    curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
    install minikube-linux-amd64 /usr/local/bin/minikube
    curl -sfL https://get.k3s.io | sh -
    # sudo sh -c 'rabbitmqadmin --bash-completion > /etc/bash_completion.d/rabbitmqadmin'
    # sudo sh -c 'rabbitmqadmin --bash-completion > /etc/bash_completion.d/rabbitmqadmin'
    complete -C '/usr/local/bin/aws_completer' aws
}
install_mlTools(){
    pip install --upgrade TensorFlow
}


main(){
    install_req
    install_amazon_stack
    install_Docker
    install_buildTools
    # install_opstools
    # install_buildTools
    install_devTools
    #install_mlTools
}

main
```


## helm practices: Deploy Portainer using Helm Chart

### install helm
```bash
curl https://raw.githubusercontent.com/helm/helm/master/scripts/get-helm-3 | bash
```

### Deploy Portainer using Helm Chart

details:
* https://portainer.github.io/k8s/charts/portainer/
* https://github.com/portainer/k8s

Before proceeding, ensure to create a namespace in advance. For instance:
```
kubectl create namespace portainer
```

#### Install the chart repository
```bash
helm repo add portainer https://portainer.github.io/k8s/
helm repo update
```

#### Testing the Chart
```bash
helm install --dry-run --debug portainer -n portainer deploy/helm/portainer
```

#### install

option 1:

```bash
helm install --create-namespace -n portainer portainer portainer/portainer
```
option 2:

```bash
helm upgrade -i -n portainer portainer portainer/portainer
```

#### remove
```bash
helm delete -n portainer portainer
kubectl delete namespace portainer
```

#### helm pull

```bash
helm pull portainer/portainer --untar
```

#### helm template

```bash
helm template -f ./chart-values/ironshield-webrisk-prd.yaml ironshield-webrisk-prd ./charts/ironshield-webrisk/ > ./outputs/ironshield-webrisk-prd-deployment.yaml
```
#### local install
```bash
helm install -f ./chart-values/ironshield-webrisk-prd.yaml ironshield-webrisk-prd  ./charts/ironshield-webrisk/
helm upgrade -f ./chart-values/ironshield-webrisk-prd.yaml ironshield-webrisk-prd  ./charts/ironshield-webrisk/
helm uninstall is-ironshield-webrisk-prd

```