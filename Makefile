default: debug
install: install-debug

build.xml:
	android update project -p . -t android-16 -n "MuPDF"

generate:
	make -C libmupdf generate

jni-debug: build.xml generate
	ndk-build -j8 APP_BUILD_SCRIPT=libmupdf/platform/java/Android.mk APP_PROJECT_PATH=. APP_PLATFORM=android-16 APP_OPTIM=debug APP_ABI=x86,armeabi-v7a
jni-release: build.xml generate
	ndk-build -j8 APP_BUILD_SCRIPT=libmupdf/platform/java/Android.mk APP_PROJECT_PATH=. APP_PLATFORM=android-16 APP_OPTIM=release APP_ABI=all

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
	rm -f build.xml local.properties project.properties proguard-project.txt
	rm -rf bin gen libs obj
