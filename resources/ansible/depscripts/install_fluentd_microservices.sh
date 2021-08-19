#!/usr/bin/env bash

_FLUENTD_VER="3.4.1-0"
instance_name=$(ec2metadata --instance-id)
instance_type=$(ec2metadata --instance-type)
instance_ip=$(ec2metadata --local-ipv4)
instance_az=$(ec2metadata --availability-zone)



curl -Ls http://packages.treasuredata.com.s3.amazonaws.com/3/ubuntu/xenial/pool/contrib/t/td-agent/td-agent_${_FLUENTD_VER}_amd64.deb -o td-agent.deb
dpkg -i td-agent.deb

rm -Rf /var/log/td-agent/*

td-agent-gem install fluent-plugin-secure-forward
td-agent-gem install fluent-plugin-record-reformer

  cat << EOF > /lib/systemd/system/td-agent.service
[Unit]
Description=td-agent: Fluentd based data collector for Treasure Data
Documentation=https://docs.treasuredata.com/articles/td-agent
After=network-online.target
Wants=network-online.target

[Service]
User=root
Group=root
LimitNOFILE=65536
Environment=LD_PRELOAD=/opt/td-agent/embedded/lib/libjemalloc.so
Environment=GEM_HOME=/opt/td-agent/embedded/lib/ruby/gems/2.4.0/
Environment=GEM_PATH=/opt/td-agent/embedded/lib/ruby/gems/2.4.0/
Environment=FLUENT_CONF=/etc/td-agent/td-agent.conf
Environment=FLUENT_PLUGIN=/etc/td-agent/plugin
Environment=FLUENT_SOCKET=/var/run/td-agent/td-agent.sock
Environment=TD_AGENT_OPTIONS=
EnvironmentFile=-/etc/sysconfig/td-agent
PIDFile=/var/run/td-agent/td-agent.pid
RuntimeDirectory=td-agent
Type=forking
ExecStart=/opt/td-agent/embedded/bin/fluentd --log /var/log/td-agent/td-agent.log --daemon /var/run/td-agent/td-agent.pid $TD_AGENT_OPTIONS
ExecStop=/bin/kill -TERM ${MAINPID}
ExecReload=/bin/kill -HUP ${MAINPID}
Restart=always
TimeoutStopSec=120

[Install]
WantedBy=multi-user.target

EOF

  cat << EOF > /etc/td-agent/td-agent.conf
<source>
  @type tail
  path /var/lib/docker/containers/*/*-json.log
  pos_file /var/log/td-agent/tmp/fluentd-docker.pos
  time_format %Y-%m-%dT%H:%M:%S
  tag docker.*
  format json
  read_from_head true

</source>
<match docker.var.lib.docker.containers.*.*.log>
  @type record_reformer
  container_id \${tag_parts[5]}
  tag docker.all
</match>
<filter **>
  @type record_transformer
  enable_ruby true
  <record>
    hostname "#{Socket.gethostname}"
    instance_name "i-0bdb4fd56939d975a"
    instance_type "m5.large"
    instance_ip "10.0.1.104"
    instance_az "us-east-1b"
  </record>
</filter>
<match **>
  @type copy
  <store>
        @log_level debug
        @type forward
        send_timeout 60s
        recover_wait 10s
        hard_timeout 60s

        <server>
        host log-fwd.bvdwnt.co
        port 24224
        weight 60
        </server>
  </store>
  <store>
    @type stdout
  </store>
</match>
EOF
  systemctl daemon-reload
  systemctl enable td-agent
  systemctl restart td-agent
