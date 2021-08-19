#!/usr/bin/env bash

CADVISOR_VERSION="v0.33.0"
curl -LO https://github.com/google/cadvisor/releases/download/${CADVISOR_VERSION}/cadvisor
useradd --no-create-home --shell /bin/false cadvisor
curl -LO https://github.com/google/cadvisor/releases/download/${CADVISOR_VERSION}/cadvisor
mv cadvisor /usr/local/bin
chown cadvisor:cadvisor /usr/local/bin/cadvisor
chmod +xxx /usr/local/bin/cadvisor

cat << EOF >  /etc/systemd/system/cadvisor.service
[Unit]
Description=cadvisor
Wants=network-online.target
After=network-online.target

[Service]
User=root
Group=root
Type=simple
ExecStart=/usr/local/bin/cadvisor -port 9101

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable cadvisor
systemctl start cadvisor
