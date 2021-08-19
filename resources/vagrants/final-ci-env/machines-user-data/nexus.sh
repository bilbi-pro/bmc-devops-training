#!/bin/bash

##PARAMS
## END PARAMS

function installPreReq(){
    echo "±±±±±±±±±±±±±>installPreReq"
    yum update -y
    yum install -y yum-utils git jq aws-cli docker bind-utils nano java wget unzip bash-completion
}

function installNexusArtifactory(){
  NEXUS_REPO_URI="https://sonatype-download.global.ssl.fastly.net/nexus/3/nexus-3.15.2-01-unix.tar.gz"
  mkdir /app && cd /app
  wget $NEXUS_REPO_URI
  tar -xvf nexus-*.tar.gz
  rm -f nexus-*.tar.gz
  mv nexus-* nexus
  adduser nexus
  chown -R nexus:nexus /app/nexus
  echo -e "\nrun_as_user=\"nexus\"" >> /app/nexus/bin/nexus.rc

  sudo ln -s /app/nexus/bin/nexus /etc/init.d/nexus
  chkconfig --add nexus
  chkconfig --levels 345 nexus on
  chown -R nexus:nexus /app/
  systemctl enable nexus
  systemctl start nexus
}


function main(){

  installPreReq
  installNexusArtifactory
}
main
