
FILES_BIN="iDecoders.dll iIVS_Sdk.dll iDrawsLib.dll iLPRSDK.dll"

for file in $FILES_BIN; do
cp -f -u -v ../../../../SDK/iLPRClientSDK/bin/$file ./ibin/
done
