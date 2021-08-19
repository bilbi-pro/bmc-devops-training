

#!/usr/bin/env bash

PUSHGATEWAY_VER="0.7.0"
useradd --no-create-home --shell /bin/false pushgateway
curl -LO https://github.com/prometheus/pushgateway/releases/download/v${PUSHGATEWAY_VER}/pushgateway-${PUSHGATEWAY_VER}.linux-amd64.tar.gz
tar xvf pushgateway-*.tar.gz
cp pushgateway-*/pushgateway /usr/local/bin
chown pushgateway:pushgateway /usr/local/bin/pushgateway
rm -rf pushgateway-*.tar.gz pushgateway-*.linux-amd64
cat << EOF >  /etc/systemd/system/pushgateway.service
[Unit]
Description=pushgateway
Wants=network-online.target
After=network-online.target

[Service]
User=pushgateway
Group=pushgateway
Type=simple
ExecStart=/usr/local/bin/pushgateway

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable pushgateway
systemctl start pushgateway
#systemctl status node_exporter
