#!/bin/bash
#
# Reports the status of the Galapagos Demo (Not found, Loading, or Ready)
#

doExit() {
  local MSG=$1
  local CODE=$2
  echo "Galapagos Demo Status: $MSG"
  exit $CODE
}

kubectl get namespace galapagos 1>/dev/null 2>&1 || doExit "Not found"

# The Demo will be ready when the import-demo-applications job is finished successfully.
JOB_STATUS=$(kubectl get job import-demo-applications --namespace galapagos -o "jsonpath='{.status.succeeded}'" | tr -d \')
if [ "$JOB_STATUS" == "" ]; then doExit "Loading" 5; fi

doExit "Ready" 0
