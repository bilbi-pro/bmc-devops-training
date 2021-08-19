# Here are some examples to how deploy remotly using Ansible some components 


commands:

# ad-hok commands:
ansible microservices -a "uname" -i inventory
ansible microservices -a "uname" -i inventory


# running playbook:

ansible-playbook -i inventory playbooks/elstic_nodeexporter.yaml

