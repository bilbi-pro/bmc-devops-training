[[ $(echo -e "test\nword") =~ ^test ]]; echo $?  #0=true
[[ $(echo -e "word\ntest") =~ ^test ]]; echo $?  #1=false
