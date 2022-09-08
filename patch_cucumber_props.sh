#!/bin/bash

platform_name=$1
bearer=$2

property_file=./src/test/resources/${platform_name}_env.properties
echo "Patching ${property_file}"
if [ -f "${property_file}" ]; then
  sed -i -e "s/bearer=.*/bearer=${bearer}/" ${property_file}
  cat ${property_file}
else
  echo "file not found"
fi


