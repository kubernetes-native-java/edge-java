#!/usr/bin/env bash
set -e

echo "trying to deploy ${APP_NAME}..."

ROOT=${GITHUB_WORKSPACE:-$(cd $(dirname $0)/../.. && pwd)}
IMAGE_NAME="gcr.io/${GCLOUD_PROJECT}/${APP_NAME}"

docker rmi -f $IMAGE_NAME || echo "no local image to delete..."
cd $ROOT && ./mvnw -DskipTests=true clean package spring-boot:build-image -Dspring-boot.build-image.imageName=$IMAGE_NAME
docker push $IMAGE_NAME

curl -H "Accept: application/vnd.github.everest-preview+json" -H "Authorization: token ${GH_PAT}" \
 --request POST  --data '{"event_type": "deploy-event"}' \
 https://api.github.com/repos/kubernetes-native-java/crm-deployment/dispatches && \
 echo "triggered a deploy-event"

