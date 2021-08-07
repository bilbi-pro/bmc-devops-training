#!/bin/bash

FOLDER_TO_ZIP=$1
REPOSITORY=$2
VERSION=$3
GROUPID=$4
ARTIFACT_ID=$5
DEPLOY_TYPE=$6
ZIP_FILE=$7
PATH=/p/gap/tools/Release_Tools/Artifactory
if [[ ! -d $Udrop/logs/target ]]
then
  echo "Creating $Udrop/logs/target"
  /bin/mkdir -p $Udrop/logs/target
fi

FILE="$ARTIFACT_ID-$VERSION.zip"

if [[ -z "$ZIP_FILE" ]] 
then
  ZIP_FILE=$Udrop/logs/target/$FILE
  LAST_FOLDER=$"${FOLDER_TO_ZIP%"${FOLDER_TO_ZIP##*[!/]}"}"
  LAST_FOLDER=$"${LAST_FOLDER##*/}"
  # zip folder to upload
  cd $FOLDER_TO_ZIP/..
  zip -r $ZIP_FILE $LAST_FOLDER
fi

#Upload to artifactory
if [[ ! -f $ZIP_FILE ]]
then
  echo "Failed to create $ZIP_FILE"
  exit 1
fi
echo "curl -u jenkins_uploader:AP6YpJtXAiv8DGMEB3DFaKJJ7X7 'http://vl-tlv-ctm-bl25/${REPOSITORY}/${GROUPID}/${ARTIFACT_ID}/${VERSION}/${FILE};build.number=${BUILD_NUMBER};build.name=${JOB_NAME} -T ${ZIP_FILE}'"

# to get checksum can cd to $Udrop/logs/target and sha1sum each file. instead of upload file by file- aql all files with build info
CHECKSUM=$(/bin/curl -u jenkins_uploader:AP6YpJtXAiv8DGMEB3DFaKJJ7X7 "http://vl-tlv-ctm-bl25/${REPOSITORY}/${GROUPID}/${ARTIFACT_ID}/${VERSION}/${FILE};buildNumber=${BUILD_NUMBER};buildName=${JOB_NAME}" -T $ZIP_FILE | \
 /bin/python2 -c "import sys, json; print json.load(sys.stdin)['checksums']['sha1']")

#create template JSON if not exist
if [[ ! -f $Udrop/logs/template.json ]]
then
  echo "Copy build info template Json to $Udrop/logs/"
  /bin/cp $PATH/template.json $Udrop/logs/
  /bin/python2 $PATH/updateTemplate.py $Udrop/logs/template.json $VERSION $JOB_NAME $BUILD_NUMBER $DEPLOY_TYPE $BUILD_URL
fi

#Create and add module
echo "Add module(artifact) to build info template"
/bin/python2 $PATH/update_build_info.py $Udrop/logs/template.json $CHECKSUM $ARTIFACT_ID $GROUPID $VERSION
/bin/curl -u jenkins_uploader:AP6YpJtXAiv8DGMEB3DFaKJJ7X7 -X PUT "http://vl-tlv-ctm-bl25/api/build" -H "Content-Type: application/json" --upload-file $Udrop/logs/template.json

