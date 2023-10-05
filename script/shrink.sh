#!/bin/bash
# Shrink photos (`*.jpg` files) in current directory, but preserves EXIF metadata.

output_dir="out"

mkdir -p "$output_dir"

for file in *.jpg; do
	if [ -f "$file" ]; then
		filename=$(basename "$file")  # Get the file name without the directory path

		echo "$output_dir/$filename" ...
		convert "$file" -resize 20% -set option:preserve-exif true "$output_dir/$filename"
	fi
done
