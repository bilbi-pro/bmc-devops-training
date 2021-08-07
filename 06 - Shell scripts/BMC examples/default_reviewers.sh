#!/bin/bash
set -eo pipefail

MYDATE=$(date +"%Y_%m_%d-%H_%M")
ADMIN="bitbucketadmin"
PSW="bitbucketAdm1n"

#check if group exist in bitbucket
SIZE="$(curl -s -u ${ADMIN}:${PSW} "${REST_URL}/api/1.0/admin/groups/?filter=${GROUP}" | jq '.size')"
if [[ -z "${GROUP}" ]] || [[ "${SIZE}" == "0" ]]
then
    echo "no group to add"
    exit 1
fi

if [[ -z "${REST_URL}" ]]
then
    REST_URL="http://vl-tlv-scm-03:7990/rest"
fi

mkdir -p /tmp/"${MYDATE}"

#get bitbucket users for active directory group
curl -s -u "${ADMIN}":"${PSW}" "${REST_URL}/api/1.0/admin/groups/more-members?context=${GROUP}" | jq '[.values[]]' > /tmp/"${MYDATE}"/users.json

IDs=$(cat /tmp/"${MYDATE}"/users.json)

# create JSON
echo -n "{\"sourceMatcher\":{\"id\":\"${SOURCE_BRANCH}\",\"type\":{\"id\":\"${SOURCE_ID}\"}},\"targetMatcher\":{\"id\":\"${TARGET_BRANCH}\",\"type\":{\"id\":\"${TARGET_ID}\"}},\"reviewers\":${IDs},\"requiredApprovals\":\"${Required_Approvals}\"}" > /tmp/"${MYDATE}"/group.json

# post users to bitbucket default reviewers
curl -u "${ADMIN}":"${PSW}" -H 'Content-Type: application/json' -s -T /tmp/"${MYDATE}"/group.json -X POST "${REST_URL}/default-reviewers/latest/projects/${PROJECT}/repos/${REPO}/condition"
RC=$?
if [[ "${RC}" == "0" ]]
then
    echo "${GROUP} was added to ${REPO} default reviewers for pull requests from ${SOURCE_BRANCH} to ${TARGET_BRANCH}"
fi

rm -rf /tmp/"${MYDATE}"

