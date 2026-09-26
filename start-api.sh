#!/bin/sh
set -eu

if [ -n "${GEOIP_LICENSE_KEY:-}" ]; then
    database_path="${GEOIP_DATABASE_PATH:-/tmp/GeoLite2-City.mmdb}"
    if [ ! -s "$database_path" ]; then
        database_directory=$(dirname "$database_path")
        temp_directory=$(mktemp -d)
        trap 'rm -rf "$temp_directory"' EXIT HUP INT TERM

        mkdir -p "$database_directory"
        printf 'url = "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City&license_key=%s&suffix=tar.gz"\n' \
            "$GEOIP_LICENSE_KEY" |
            curl --config - --location --fail --silent --show-error \
                --output "$temp_directory/geolite-city.tar.gz"

        tar -xzf "$temp_directory/geolite-city.tar.gz" \
            -C "$temp_directory" --wildcards '*/GeoLite2-City.mmdb'
        database_file=$(find "$temp_directory" -type f -name GeoLite2-City.mmdb -print -quit)
        if [ -z "$database_file" ] || [ ! -s "$database_file" ]; then
            echo "GeoLite City download completed but the database file was not found." >&2
            exit 1
        fi

        cp "$database_file" "$database_path.tmp"
        chmod 0644 "$database_path.tmp"
        mv "$database_path.tmp" "$database_path"
        echo "GeoLite City database downloaded."
    else
        echo "Using existing GeoLite City database."
    fi

    export GEOIP_DATABASE_PATH="$database_path"
else
    echo "GEOIP_LICENSE_KEY is not configured; IP geolocation is disabled." >&2
fi

exec java -Dserver.port="${PORT:-8080}" -jar /app/app.jar
