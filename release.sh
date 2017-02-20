#!/bin/bash
#
# A script to build and cook release APKs for uploading to Google Play.
# Make sure the keystore is set up in local.properties, so that 'make release'
# can build and sign a release APK.
#
# This script will automatically edit the android:versionCode by changing
# the final digit, and create one APK per ABI.

V=$(git describe --tags)

sed -i -e '/versionName/s/"[^"]*"/"'$V'"/' AndroidManifest.xml

X=1
for APP_ABI in armeabi armeabi-v7a mips x86
do
	sed -i -e '/android:versionCode/s/[0-9]"/'$X'"/' AndroidManifest.xml
	make release APP_ABI=$APP_ABI
	mv 'bin/MuPDF mini-release.apk' bin/mupdf-android-viewer-mini-$V-$APP_ABI.apk
	X=$(expr $X + 1)
done

# restore versionCode
sed -i -e '/android:versionCode/s/[0-9]"/0"/' AndroidManifest.xml
sed -i -e '/versionName/s/"[^"]*"/"'$V' (git)"/' AndroidManifest.xml
