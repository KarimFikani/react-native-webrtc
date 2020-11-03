# Building WebRTC

This document shows how to prepare a WebRTC build for its inclusion in this
plugin.

The build will be made with the `build-webrtc.py` Python script located in the
`tools/` directory.

## Preparing the build

Running the script with `--setup` will download all necessary tools for building
WebRTC. The script must be run with a target directory where all WebRTC source
code and resulting build artifacts will be placed. A `build_webrtc` directory
will be created containing it all.

The setup process only needs to be carried out once.

### iOS

```
python build-webrtc.py --setup --ios ~/webrtc-build/
```

### Android

NOTE: Make sure you have the Java JDK installed beforehand. On Debian and
Ubuntu systems this can be accomplished by installing the `default-jdk-headless`
package.

```
python build-webrtc.py --setup --android ~/webrtc-build/
```

## Selecting the branch

Once the setup process has finished, the target branch must be selected, also
adding any required cherry-picks. The following example shows how the M57 branch
was made:

```
cd ~/webrtc-build/build_webrtc/webrtc/android/src/
cd ~/webrtc-build/build_webrtc/webrtc/ios/src/
git checkout -b build-M84 refs/remotes/branch-heads/4147

git remote add atheer https://github.com/atheerent/webrtc.git
git fetch -a
git checkout -b M84-atheer
git pull atheer M84-atheer
cd ..
```

Now the code is ready for building!

Notice that since M79 chromium changed the branch naming scheme, for example M84 is WebRTC branch 4187.
For a full list of branches, see: https://chromiumdash.appspot.com/branches

## Building

### iOS

```
python build-webrtc.py --build --ios ~/webrtc-build
```

The build artifacts will be located in `~/webrtc-build/build_webrtc/build/ios/`.

### Android

**NOTE**: WebRTC for Android can only be built on Linux at the moment.

```
python build-webrtc.py --build --android ~/webrtc-build
```

The build artifacts will be located in `~/webrtc-build/build/android/`.

### Making debug builds

Debug builds can be made by adding `--debug` together with `--build`. For
example, to make a debug iOS build:

```
python build-webrtc.py --build --ios --debug ~/webrtc-build/
```
