
FILES_BIN="iDecoders.dll VZ_Sdk.dll VzDrawsLib.dll VzLPRSDK.dll"

for file in $FILES_BIN; do
cp -f -u -v ../../../../SDK/VzLPRClientSDK/bin/$file ./bin/
done
