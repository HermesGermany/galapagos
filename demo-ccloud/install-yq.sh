#!/bin/bash

# Small script to install yq binary, depending on current OS and arch.
# Uses shlib scripts to determine OS and arch, see https://github.com/client9/shlib

# use fix commit hash to not be vulnerable to later code injections
SHLIB_BASE_URL=https://raw.githubusercontent.com/client9/shlib/1f866332017d9d635729042eae83be53308ac29a

YQ_BASE_URL="https://github.com/mikefarah/yq/releases/download/v4.26.1"

curl -sL "$SHLIB_BASE_URL/uname_os.sh" > bin/uname_os.sh
curl -sL "$SHLIB_BASE_URL/uname_arch.sh" > bin/uname_arch.sh
source bin/uname_os.sh
source bin/uname_arch.sh

OS_NAME=$(uname_os)
ARCH_NAME=$(uname_arch)
echo "Downloading yq for ${OS_NAME}_${ARCH_NAME}..."

FILE_SUFFIX=""
if [ "$OS_NAME" == "windows" ]
then
  FILE_SUFFIX=".exe"
fi

curl -sLSf "$YQ_BASE_URL/yq_${OS_NAME}_${ARCH_NAME}${FILE_SUFFIX}" > "./bin/yq${FILE_SUFFIX}"
