#!/bin/sh

godot_lib=~/projects/teslatech/callbreak/android/build/libs/release/godot-lib.release.aar

cp -f "$godot_lib" libs/

./gradlew build
cp app/build/outputs/aar/GodotGooglePlayBilling-9.1.0-release.aar ../callbreak/android/plugins/

