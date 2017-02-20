default: debug
install: install-debug

APP_ABI ?= x86,armeabi-v7a

build.xml:
	android update project -p . -t android-21 -n "MuPDF mini"

generate:
	make -C libmupdf generate

jni-debug: build.xml generate
	ndk-build -j4 APP_BUILD_SCRIPT=libmupdf/platform/java/Android.mk APP_PROJECT_PATH=. APP_PLATFORM=android-13 APP_ABI=$(APP_ABI) APP_OPTIM=debug
jni-release: build.xml generate
	ndk-build -j4 APP_BUILD_SCRIPT=libmupdf/platform/java/Android.mk APP_PROJECT_PATH=. APP_PLATFORM=android-13 APP_ABI=$(APP_ABI) APP_OPTIM=release

debug: jni-debug
	ant debug
release: jni-release
	ant release
install-debug: jni-debug
	ant debug install
install-release: jni-release
	ant release install

run: install-debug
	adb shell am start -n com.artifex.mupdf.mini/.LibraryActivity

clean:
	rm -f build.xml
	rm -rf bin gen libs obj
