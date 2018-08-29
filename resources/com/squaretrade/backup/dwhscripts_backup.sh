#!/usr/bin/env bash
#set -xe
base_dir=$1
deploy_dir_name=$2
deploy_dir="${base_dir}/${deploy_dir_name}"
backup_dir="${base_dir}/${deploy_dir_name}-old"
if [ ! -d "${deploy_dir}" ]; then
    echo "Creating deployment directory: ${deploy_dir}..."
    mkdir -p "${deploy_dir}"
fi
if [ -d "${backup_dir}" ]; then
    echo "Removing backup directory ${backup_dir}..."
    rm -Rf "${backup_dir}"
fi
echo "Backing up $deploy_dir to ${backup_dir}..."
mv "${deploy_dir}" "${backup_dir}"
