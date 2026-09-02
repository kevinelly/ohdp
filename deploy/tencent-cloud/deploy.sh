#!/usr/bin/env sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "usage: deploy.sh RELEASE_DIRECTORY" >&2
  exit 2
fi

release_dir="$1"
root_dir="/opt/bhfyzx"
shared_dir="$root_dir/shared"
env_file="$shared_dir/.env"

mkdir -p "$shared_dir"
chmod 700 "$shared_dir"
umask 077

if [ ! -f "$env_file" ]; then
  mysql_password="$(openssl rand -hex 24)"
  mysql_root_password="$(openssl rand -hex 32)"
  admin_password="$(openssl rand -hex 10)"
  {
    echo "WEB_PORT=8088"
    echo "MYSQL_DATABASE=ohdp"
    echo "MYSQL_USER=ohdp"
    echo "MYSQL_PASSWORD=$mysql_password"
    echo "MYSQL_ROOT_PASSWORD=$mysql_root_password"
    echo "ADMIN_USERNAME=admin"
    echo "ADMIN_INITIAL_PASSWORD=$admin_password"
    echo "TZ=Asia/Shanghai"
  } > "$env_file"
fi

chmod 600 "$env_file"
cp "$env_file" "$release_dir/.env"
chmod 600 "$release_dir/.env"

docker compose --env-file "$release_dir/.env" -f "$release_dir/compose.yaml" config --quiet
docker compose --env-file "$release_dir/.env" -f "$release_dir/compose.yaml" build
docker compose --env-file "$release_dir/.env" -f "$release_dir/compose.yaml" up -d --wait

if [ -L "$root_dir/current" ]; then
  previous_target="$(readlink -f "$root_dir/current")"
  ln -sfn "$previous_target" "$root_dir/previous"
fi
ln -sfn "$release_dir" "$root_dir/current"

docker compose --env-file "$release_dir/.env" -f "$release_dir/compose.yaml" ps
