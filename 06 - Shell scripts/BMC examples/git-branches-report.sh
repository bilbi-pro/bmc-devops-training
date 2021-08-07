#!/bin/bash -e
# Credit http://stackoverflow.com/a/2514279
# Script to get report of git repo, need to be run from the repo checkout path
function generate() {
  for branch in $(git branch --"$1" | sed -e s/\*//g | grep -vE 'HEAD|master'); do echo -e \"$branch\"","$2"",$(git show --format="%ci,%ce,\"%s\"" $branch | head -n 1); done
}
reposLoc=/opt/atlassian/bitbucket/data/application-data/bitbucket/shared/data/repositories
repositories=$(echo $repositories | tr ',' ' ')

cd $reposLoc
# getting reponum
repoNum=$(ls */repository-config | xargs grep repository | grep -w "$1" | grep -v "$1-"| cut -d "/" -f1 | head -n1)
repoAddr=$reposLoc/$repoNum
toFile="$Udrop/logs/$1.csv"

su - bldrtlv -c "touch $toFile;chmod 777 $toFile"
#genrating report
cd $repoAddr
(
  echo "Branch,Merged,Last commit date,Last committer mail,Commit message"
  generate merged V
  generate no-merged X
) | tee $toFile
