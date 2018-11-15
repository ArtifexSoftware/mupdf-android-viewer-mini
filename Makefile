# This is a very simple Makefile that calls 'gradlew' to do the heavy lifting.
#
# The tool 'adb' must be on the path, so that we can find the Android SDK.

ANDROID_HOME := $(shell which adb | sed 's,/platform-tools/adb,,')

default: assembleDebug
release: assembleRelease
install: installDebug

generate:
	if [ -f jni/Makefile ]; then make -C jni generate; fi
assembleDebug: generate
	ANDROID_HOME=$(ANDROID_HOME) ./gradlew assembleDebug
assembleRelease: generate
	ANDROID_HOME=$(ANDROID_HOME) ./gradlew assembleRelease
installDebug: generate
	ANDROID_HOME=$(ANDROID_HOME) ./gradlew installDebug
lint:
	ANDROID_HOME=$(ANDROID_HOME) ./gradlew lint
archive: generate
	ANDROID_HOME=$(ANDROID_HOME) ./gradlew uploadArchives
sync: archive
	rsync -av MAVEN/com/ ghostscript.com:/var/www/maven.ghostscript.com/com/

run: install
	adb shell am start -n com.artifex.mupdf.mini.app/.LibraryActivity

clean:
	rm -rf .gradle build
	rm -rf jni/.externalNativeBuild jni/.gradle jni/build jni/libmupdf/generated
	rm -rf lib/.gradle lib/build
	rm -rf app/.gradle app/build
