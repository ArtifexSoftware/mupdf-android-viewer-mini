assemble: assembleDebug
install: installDebug

ANDROID_HOME := $(shell which adb | sed 's,/platform-tools/adb,,')
ANDROID_SERIAL ?= $(shell adb devices | grep 'device$$' | cut -f1 | tr '\n' , | sed s/,$$//)

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
	IFS=","; DEVICES="$(ANDROID_SERIAL)"; \
	for device in $$DEVICES; do \
		adb -s $$device shell input keyevent KEYCODE_WAKEUP; \
		adb -s $$device shell am start -n com.artifex.mupdf.mini/.LibraryActivity; \
	done

distclean:
	make -C libmupdf nuke
	rm -rf .externalNativeBuild .gradle build
	rm -rf .idea/ local.properties mupdf-android-viewer-mini.iml
