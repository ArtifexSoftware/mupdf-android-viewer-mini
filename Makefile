assemble: assembleDebug
install: installDebug

ANDROID_HOME := $(shell which adb | sed 's,/platform-tools/adb,,')

generate:
	make -j4 -C libmupdf generate

assembleDebug: generate
	ANDROID_HOME=$(ANDROID_HOME) ./gradlew assembleDebug
assembleRelease: generate
	ANDROID_HOME=$(ANDROID_HOME) ./gradlew assembleRelease
installDebug: generate
	ANDROID_HOME=$(ANDROID_HOME) ./gradlew installDebug
lint:
	ANDROID_HOME=$(ANDROID_HOME) ./gradlew lint
clean:
	ANDROID_HOME=$(ANDROID_HOME) ./gradlew clean

run: installDebug
	adb shell am start -n com.artifex.mupdf.mini/.LibraryActivity

distclean:
	make -C libmupdf nuke
	rm -rf .externalNativeBuild .gradle build
	rm -rf .idea/ local.properties mupdf-android-viewer-mini.iml
